package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {

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

            // Entry gate: block BEFORE running any handler in this batch. Each entry-gated stage
            // that is still PENDING (i.e. hasn't been approved yet) halts the workflow one stage
            // at a time — a stage this call approves becomes PENDING-and-approved and is excluded
            // here on the next resume, so every entry-gated sibling in a batch is naturally
            // visited in turn across repeated resumes without needing a batch-wide pre-scan.
            for (StageDefinition stage : pending) {
                if (stage.entryApproval()) {
                    WorkflowStepEntity step = requireStep(workflowId, planVersion, stage.name());
                    if (!isApproved(step)) {
                        enterPendingApproval(step);
                        instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                        instanceRepository.save(instance);
                        return; // halt entirely; handler has NOT run; approve() resumes
                    }
                }
            }

            if (!pending.isEmpty()) {
                runBatch(workflowId, planVersion, pending, ctx);

                instance.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", ctx.artifacts()));
                instanceRepository.save(instance);
            }

            // Exit gate: block AFTER running, before any dependent batch proceeds. This scans
            // EVERY exit-gated stage in the FULL batch (not just the subset that was `pending`
            // THIS call) and requires each one to carry its own approval before dependents
            // proceed. Round-6 correction (Codex REQUEST AMENDMENTS item 2): the prior version
            // scanned only `pending` and returned on the FIRST unapproved sibling it found: once
            // that sibling was approved and runFrom() resumed, the OTHER exit-gated siblings —
            // already SUCCEEDED from the batch's one-and-only run — were no longer `pending`
            // (PENDING-filtered), so they were silently skipped forever, never receiving their own
            // gate. Re-deriving "still needs a decision" from persisted step status on every call
            // (not from the transient `pending` list) makes this idempotent across any number of
            // partial resumes.
            List<WorkflowStepEntity> stillWaitingForApproval = new ArrayList<>();
            for (StageDefinition stage : batch) {
                if (!stage.exitApproval()) continue;
                WorkflowStepEntity step = requireStep(workflowId, planVersion, stage.name());
                StepStatus status = step.getStatus();
                boolean readyForGate = status == StepStatus.SUCCEEDED || status == StepStatus.WAITING_APPROVAL;
                // Round-6b correction (independent-review finding): gate on "has ANY decision yet"
                // (approved OR rejected), not "has an approved decision" alone. A rejected exit
                // gate leaves its step SUCCEEDED (same status a normal completion leaves it in) —
                // checking isApproved() here would treat that as "still needs gating" and hand it a
                // fresh WAITING_APPROVAL on the next resume, letting a second decision overturn the
                // original rejection. hasAnyDecision() makes an already-decided step immune to
                // re-gating regardless of which way it was decided.
                if (readyForGate && !hasAnyDecision(step)) {
                    if (status != StepStatus.WAITING_APPROVAL) {
                        enterPendingApproval(step);
                    }
                    stillWaitingForApproval.add(step);
                }
            }
            if (!stillWaitingForApproval.isEmpty()) {
                instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                instanceRepository.save(instance);
                return; // halt; handlers already ran and are WAITING_APPROVAL; approve() resumes
            }
        }

        instance.setStatus(WorkflowStatus.COMPLETED);
        instanceRepository.save(instance);
        recordEvent(workflowId, "engine", "workflow-engine", "workflow_completed", null, "OK", null);
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
        // Round-6 correction (Codex REQUEST AMENDMENTS item 1): validate the decision value itself
        // before touching any state — a decision that is neither "approved" nor "rejected" must
        // never be silently treated as an implicit approval.
        if (!"approved".equals(decision) && !"rejected".equals(decision)) {
            throw new InvalidApprovalDecisionException(
                    "decision must be 'approved' or 'rejected', got: " + decision);
        }

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId)
                .orElseThrow(() -> new NoPendingApprovalException("unknown workflow: " + workflowId));

        // Round-6b correction (independent-review finding): a terminal workflow accepts no further
        // decisions. Without this, approving a DIFFERENT sibling in the same exit-gated batch after
        // one sibling was already rejected (which sets the workflow FAILED) would call runFrom()
        // again, which resurrects the instance from FAILED back to RUNNING and re-derives the exit
        // gate from step status alone — silently re-opening and letting a second decision overturn
        // the original rejection.
        if (instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.COMPLETED) {
            throw new NoPendingApprovalException(
                    "workflow " + workflowId + " is already terminal (" + instance.getStatus() + ")");
        }
        int planVersion = instance.getPlanVersion();

        StageRegistration registration = registry.get(workflowId);
        StageDefinition stage = registration == null ? null : registration.stages().stream()
                .filter(s -> s.name().equals(stepName)).findFirst().orElse(null);
        if (stage == null) {
            throw new NoPendingApprovalException("unknown step: " + stepName);
        }
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName)
                .orElseThrow(() -> new NoPendingApprovalException("unknown step: " + stepName));

        // Reject premature (step never reached its gate), duplicate (step already decided), and
        // non-gate (stage has neither entryApproval nor exitApproval) approval attempts alike: a
        // step only ever carries WAITING_APPROVAL while a gate genuinely awaits a decision on it.
        if (step.getStatus() != StepStatus.WAITING_APPROVAL) {
            throw new NoPendingApprovalException(
                    "no approval currently pending on step " + stepName + " (status: " + step.getStatus() + ")");
        }

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
        recordEvent(workflowId, "human", actor, "approval_" + decision + ":" + stepName, stepName, "OK", reason);

        if ("rejected".equals(decision)) {
            if (stage.entryApproval()) {
                step.setStatus(StepStatus.FAILED);
            } else {
                step.setStatus(StepStatus.SUCCEEDED);
            }
            stepRepository.save(step);
            instance.setStatus(WorkflowStatus.FAILED);
            instanceRepository.save(instance);
            return;
        }

        if (stage.entryApproval()) {
            step.setStatus(StepStatus.PENDING);
        } else {
            step.setStatus(StepStatus.SUCCEEDED);
        }
        stepRepository.save(step);
        instance.setStatus(WorkflowStatus.RUNNING);
        instanceRepository.save(instance);
        runFrom(workflowId);
    }

    // Round-6 correction (Codex REQUEST AMENDMENTS item 1): stepInstanceId — not stepName — is the
    // authoritative key for "has THIS step instance been approved".
    private boolean isApproved(WorkflowStepEntity step) {
        return !approvalRepository.findByStepInstanceIdAndDecision(step.getId(), "approved").isEmpty();
    }

    private boolean hasAnyDecision(WorkflowStepEntity step) {
        return approvalRepository.existsByStepInstanceId(step.getId());
    }

    private void enterPendingApproval(WorkflowStepEntity step) {
        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);
    }

    private void runStage(String workflowId, int planVersion, StageDefinition stage, WorkflowContext ctx) {
        WorkflowStepEntity step = requireStep(workflowId, planVersion, stage.name());
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        stepRepository.save(step);

        Map<String, Object> result = stage.handler().run(ctx);

        step.setStatus(StepStatus.SUCCEEDED);
        step.setEndedAt(Instant.now());
        step.setResult(result != null ? result : Map.of());
        stepRepository.save(step);
        recordEvent(workflowId, "agent", stage.name(), "stage_succeeded:" + stage.name(), stage.name(), "OK", null);
    }

    private void recordEvent(String workflowId, String actorType, String actorId, String action,
                              String affectedArtifact, String result, String reason) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(workflowId);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setAction(action);
        event.setAffectedArtifact(affectedArtifact);
        event.setResult(result);
        event.setReason(reason);
        eventRepository.save(event);
    }

    private StepStatus stepStatus(String workflowId, int planVersion, String name) {
        return requireStep(workflowId, planVersion, name).getStatus();
    }

    private WorkflowStepEntity requireStep(String workflowId, int planVersion, String name) {
        return stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, name).orElseThrow();
    }

    private record StageRegistration(List<StageDefinition> stages, DependencyGraph graph) { }
}
