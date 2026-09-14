package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WorkflowEngine {

    private final Map<String, StageRegistration> registry = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;
    private final TransactionTemplate transactionTemplate;

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
                // Round-6c correction: freeze the artifacts map as it stood at the START of this
                // batch (before any handler in it runs) and persist THIS snapshot on every step in
                // the batch — not the workflow's current/final artifact map re-read later. Handlers
                // in the same batch run concurrently and may mutate ctx.artifacts() as they go, so
                // capturing once, up front, is what makes "inputs available when the stage began"
                // well-defined instead of a race against siblings.
                Map<String, Object> batchStartArtifacts = Map.copyOf(ctx.artifacts());
                runBatch(workflowId, planVersion, pending, ctx, batchStartArtifacts);

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
        // actorType "orchestrator" (not "engine"): openapi.yaml's AuditEventResponse.actorType
        // enum is {agent, human, orchestrator} — the engine completing a workflow on its own
        // initiative is exactly the "orchestrator" case.
        recordEvent(workflowId, "orchestrator", "workflow-engine", "workflow_completed", null, "OK", null);
    }

    private void runBatch(String workflowId, int planVersion, List<StageDefinition> pending, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (StageDefinition stage : pending) {
                futures.add(pool.submit(() -> runStage(workflowId, planVersion, stage, ctx, inputsSnapshot)));
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
        // Round-6c correction: actor/reason are REQ-013's "recorded with rationale" and
        // data-model.md's decided_by fields — required for any row this engine actually inserts
        // (see ApprovalEntity's Round-6c note: nullable at the schema level only to allow a future
        // pending-row-before-decision pattern this engine doesn't use).
        if (actor == null || actor.isBlank()) {
            throw new InvalidApprovalDecisionException("actor must be present and non-blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidApprovalDecisionException("reason must be present and non-blank");
        }

        // Round-6c correction (item 1, atomic/one-shot decision recording): the read-check-write
        // sequence that decides whether THIS call wins the gate runs inside one transaction, with
        // the target step's row pessimistically locked, so two concurrent approve() calls on the
        // SAME step instance are fully serialized — the loser re-reads the step under lock only
        // after the winner has committed, sees the already-decided status, and is rejected exactly
        // like any other "no pending gate" case. The step_instance_id unique constraint on
        // ApprovalEntity is a second, independent backstop against a duplicate insert.
        DecisionOutcome outcome = recordDecisionAtomically(workflowId, stepName, decision, actor, reason);
        if (!outcome.rejected()) {
            runFrom(workflowId);
        }
    }

    private DecisionOutcome recordDecisionAtomically(String workflowId, String stepName, String decision,
                                                       String actor, String reason) {
        try {
            return recordDecisionInTransaction(workflowId, stepName, decision, actor, reason);
        } catch (PessimisticLockingFailureException e) {
            // A concurrent approve() call on the SAME step is holding the row lock past H2's lock
            // timeout: rather than let that surface as a 500, treat it the same as losing the race
            // outright — by the time this caller could act, the winner has (or is about to have)
            // already decided this gate.
            throw new NoPendingApprovalException(
                    "no approval currently pending on step " + stepName + " (contended by a concurrent decision)");
        }
    }

    private DecisionOutcome recordDecisionInTransaction(String workflowId, String stepName, String decision,
                                                          String actor, String reason) {
        return transactionTemplate.execute(status -> {
            WorkflowInstanceEntity instance = instanceRepository.findById(workflowId)
                    .orElseThrow(() -> new NoPendingApprovalException("unknown workflow: " + workflowId));

            // Round-6b correction (independent-review finding): a terminal workflow accepts no
            // further decisions — see the original note preserved from that round below.
            // Disclosed limitation (Round-6c independent review): this read is NOT covered by the
            // step-level pessimistic lock below, so it only serializes decisions on the SAME step.
            // Two DIFFERENT exit-gated siblings decided concurrently (reject one, approve the other,
            // truly simultaneously) can each pass this check before either commits, letting the
            // approved sibling's later runFrom() call — which itself has no terminal-status guard —
            // carry a workflow the rejection already failed through to COMPLETED. Out of scope for
            // this round's three items (all specifically about a single step's own decision); would
            // need workflow-instance-level locking to close, not just this step's row lock.
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

            // Pessimistic row lock (SELECT ... FOR UPDATE): a concurrent approve() call targeting
            // this same step blocks here until this transaction commits or rolls back, then re-reads
            // the now-committed status — never a stale, pre-decision read.
            WorkflowStepEntity step = stepRepository
                    .findWithLockByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName)
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
            try {
                approvalRepository.saveAndFlush(approval);
            } catch (DataIntegrityViolationException e) {
                // Backstop for the DB unique constraint on step_instance_id: the pessimistic lock
                // above should already make this unreachable, but if it's ever hit, a second
                // decision on an already-decided step is rejected the same way a duplicate is.
                throw new NoPendingApprovalException(
                        "no approval currently pending on step " + stepName + " (already decided)");
            }
            recordEvent(workflowId, "human", actor, "approval_" + decision + ":" + stepName, stepName, "OK", reason);

            boolean rejected = "rejected".equals(decision);
            if (rejected) {
                step.setStatus(stage.entryApproval() ? StepStatus.FAILED : StepStatus.SUCCEEDED);
            } else {
                step.setStatus(stage.entryApproval() ? StepStatus.PENDING : StepStatus.SUCCEEDED);
            }
            stepRepository.save(step);
            instance.setStatus(rejected ? WorkflowStatus.FAILED : WorkflowStatus.RUNNING);
            instanceRepository.save(instance);
            return new DecisionOutcome(rejected);
        });
    }

    private record DecisionOutcome(boolean rejected) { }

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

    private void runStage(String workflowId, int planVersion, StageDefinition stage, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        WorkflowStepEntity step = requireStep(workflowId, planVersion, stage.name());
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        step.setInputsSnapshot(inputsSnapshot);
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
