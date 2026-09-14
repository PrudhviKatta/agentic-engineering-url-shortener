package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class WorkflowEngine {

    // Non-static, per-instance: same-object convenience linking submit() to its own runFrom()
    // call and any later resume within this object's lifetime — never a static/shared registry.
    // See this task's Interfaces note above.
    private final Map<String, StageRegistration> registry = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
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
        // NOTE: Task 22 intentionally builds a fresh WorkflowContext on every call
        // (no persistence yet) — this is deliberately incomplete and is exactly the
        // gap Task 24 closes. Do not "fix" it here; Task 24's failing test depends
        // on this gap existing first.
        StageRegistration registration = registry.get(workflowId);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();
        WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId());

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            List<StageDefinition> pending = batch.stream()
                    .filter(s -> stepStatus(workflowId, planVersion, s.name()) == StepStatus.PENDING)
                    .toList();
            if (pending.isEmpty()) continue;

            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (StageDefinition stage : pending) {
                    futures.add(pool.submit(() -> runStage(workflowId, planVersion, stage, ctx)));
                }
                for (Future<?> future : futures) {
                    future.get(); // propagate exceptions synchronously per batch (fail-fast)
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

        instance.setStatus(WorkflowStatus.COMPLETED);
        instance.setUpdatedAt(Instant.now());
        instanceRepository.save(instance);
        recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
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
