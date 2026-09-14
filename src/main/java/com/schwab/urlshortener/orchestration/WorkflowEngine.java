package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

// Two distinct gate types, both needed (guide requires "entry AND exit gates"):
//   entryApproval: handler must NOT run until approved (destructive/irreversible actions)
//   exitApproval:  handler runs normally; only its DEPENDENTS wait on approval
//                  (reviewing a produced design, a detected ambiguity, a readiness report)
// A single boolean cannot express both — do not collapse these back into one flag.
public class WorkflowEngine {

    // Non-static, per-instance: same-object convenience linking submit() to its own runFrom()
    // call and any later resume within this object's lifetime — never a static/shared registry.
    // See Task 22's Interfaces note.
    private final Map<String, StageRegistration> registry = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
    }

    public String submit(String feature, List<StageDefinition> stages, String correlationId) {
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        String workflowId = UUID.randomUUID().toString();
        registry.put(workflowId, new StageRegistration(stages, graph));

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature(feature);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(correlationId);
        instanceRepository.save(instance);

        int sequence = 0;
        for (StageDefinition stage : stages) {
            WorkflowStepEntity step = new WorkflowStepEntity();
            step.setWorkflowId(workflowId);
            step.setName(stage.name());
            step.setStatus(StepStatus.PENDING);
            step.setSequence(sequence++);
            step.setDependsOn(stage.dependsOn());
            step.setPlanVersion(instance.getPlanVersion());
            stepRepository.save(step);
        }

        runFrom(workflowId);
        return workflowId;
    }

    private void runFrom(String workflowId) {
        StageRegistration registration = registry.get(workflowId);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> priorDecisions =
                (List<Map<String, Object>>) instance.getContextJson().getOrDefault("decisions", new ArrayList<Map<String,Object>>());
        @SuppressWarnings("unchecked")
        Map<String, Object> priorArtifacts =
                (Map<String, Object>) instance.getContextJson().getOrDefault("artifacts", new HashMap<String,Object>());
        WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId(), priorDecisions, priorArtifacts);

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            List<StageDefinition> pending = batch.stream()
                    .filter(s -> stepStatus(workflowId, planVersion, s.name()) == StepStatus.PENDING)
                    .toList();
            if (pending.isEmpty()) continue;

            // Entry gate: block BEFORE running any handler in this batch.
            for (StageDefinition stage : pending) {
                if (stage.entryApproval() && !isApproved(workflowId, planVersion, stage.name())) {
                    enterPendingApproval(workflowId, planVersion, stage.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    instanceRepository.save(instance);
                    return; // halt entirely; handler has NOT run; approve() resumes
                }
            }

            runBatch(workflowId, planVersion, pending, ctx);

            // Persist lineage BEFORE the exit-gate check, so a decision recorded by an
            // exit-gated stage's own handler survives even though this batch then halts.
            instance.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", ctx.artifacts()));
            instanceRepository.save(instance);

            // Exit gate: block AFTER running, before any dependent batch proceeds.
            for (StageDefinition stage : pending) {
                if (stage.exitApproval() && !isApproved(workflowId, planVersion, stage.name())) {
                    enterPendingApproval(workflowId, planVersion, stage.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    instanceRepository.save(instance);
                    return; // halt; handler already ran and is SUCCEEDED-turned-WAITING_APPROVAL
                }
            }
        }

        instance.setStatus(WorkflowStatus.COMPLETED);
        instanceRepository.save(instance);
        recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
    }

    private void runBatch(String workflowId, int planVersion, List<StageDefinition> pending, WorkflowContext ctx) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (StageDefinition stage : pending) {
                futures.add(pool.submit(() -> runStage(workflowId, planVersion, stage, ctx)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    public void approve(String workflowId, String stepName, String decision, String actor, String reason) {
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();
        StageDefinition stage = registry.get(workflowId).stages().stream()
                .filter(s -> s.name().equals(stepName)).findFirst().orElseThrow();
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName).orElseThrow();

        ApprovalEntity approval = new ApprovalEntity();
        approval.setWorkflowId(workflowId);
        approval.setPlanVersion(planVersion);
        approval.setStepInstanceId(step.getId());
        approval.setStepName(stepName);
        approval.setDecidedAt(Instant.now());
        approval.setDecision(decision);
        approval.setDecidedBy(actor);
        approval.setReason(reason);
        approvalRepository.save(approval);
        recordEvent(workflowId, "human", "approval_" + decision + ":" + stepName, "OK", reason);

        if ("rejected".equals(decision)) {
            // Branch on stage.entryApproval() directly, not on the step's current status.
            if (stage.entryApproval()) {
                step.setStatus(StepStatus.FAILED); // entry-gate reject: handler never ran
            } else {
                step.setStatus(StepStatus.SUCCEEDED); // exit-gate reject: handler already completed
            }
            stepRepository.save(step);
            instance.setStatus(WorkflowStatus.FAILED);
            instanceRepository.save(instance);
            return;
        }

        // approved: put the step back in a state runFrom() will act on correctly.
        if (stage.entryApproval()) {
            step.setStatus(StepStatus.PENDING); // let it run for the first time now
        } else {
            step.setStatus(StepStatus.SUCCEEDED); // exit-gate: already ran; unblock dependents
        }
        stepRepository.save(step);
        instance.setStatus(WorkflowStatus.RUNNING);
        instanceRepository.save(instance);
        runFrom(workflowId);
    }

    private boolean isApproved(String workflowId, int planVersion, String stepName) {
        return !approvalRepository.findByWorkflowIdAndPlanVersionAndStepNameAndDecision(
                workflowId, planVersion, stepName, "approved").isEmpty();
    }

    private void enterPendingApproval(String workflowId, int planVersion, String stepName) {
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName).orElseThrow();
        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);
    }

    private void runStage(String workflowId, int planVersion, StageDefinition stage, WorkflowContext ctx) {
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stage.name()).orElseThrow();
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        stepRepository.save(step);

        Map<String, Object> result = stage.handler().run(ctx);

        step.setStatus(StepStatus.SUCCEEDED);
        step.setEndedAt(Instant.now());
        step.setResult(result != null ? result : Map.of());
        stepRepository.save(step);
        recordEvent(workflowId, "agent", "stage_succeeded:" + stage.name(), "OK", null);
    }

    private void recordEvent(String workflowId, String actorType, String action, String result, String reason) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(workflowId);
        event.setActorType(actorType);
        event.setAction(action);
        event.setResult(result);
        event.setReason(reason);
        eventRepository.save(event);
    }

    private StepStatus stepStatus(String workflowId, int planVersion, String name) {
        return stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, name).orElseThrow().getStatus();
    }

    private record StageRegistration(List<StageDefinition> stages, DependencyGraph graph) { }
}
