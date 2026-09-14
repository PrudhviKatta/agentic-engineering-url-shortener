package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyCheckResult;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.policy.PolicyOutcome;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.PermanentStageException;
import com.schwab.urlshortener.reliability.RetryExecutor;
import com.schwab.urlshortener.reliability.TimeoutExecutor;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WorkflowEngine {

    // Codex REQUEST AMENDMENTS item 5: base delay for RetryExecutor's exponential backoff
    // (backoffMillis * 2^attempt). Not exposed on StageDefinition/openapi.yaml — a fixed, disclosed
    // constant rather than new per-stage configuration surface this round didn't ask for.
    private static final long RETRY_BACKOFF_BASE_MILLIS = 100L;

    private final Map<String, StageRegistration> registry = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;
    private final TransactionTemplate transactionTemplate;
    // Task 28/29: all three nullable — policy enforcement (and exceptions to it) are opt-in via
    // the overloaded constructors below. The shared @Component bean (used by
    // ScenarioRunner/WorkflowController today) still resolves via the 5-arg constructor with all
    // three null, since no PolicyEngine is registered as a Spring bean yet — wiring a real
    // policy-aware production instance remains a later task's job, not this one's.
    private final PolicyEvaluationJpaRepository policyEvaluationRepository;
    private final PolicyEngine policyEngine;
    private final PolicyExceptionJpaRepository policyExceptionRepository;
    // Task 33: nullable — compensation is opt-in, same pattern as the policy fields above.
    private final CompensationRegistry compensationRegistry;

    @Autowired
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, null, null, null, null);
    }

    // Task 28's constructor shape, preserved unchanged so WorkflowEnginePolicyEnforcementTest
    // keeps compiling and passing exactly as written — policyExceptionRepository defaults to null
    // (matches hasApprovedUnexpiredException's Task-28-era always-false behavior for any caller
    // that doesn't also opt into Task 29's exception repository).
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, null, null);
    }

    // Task 29's constructor shape, preserved unchanged so PolicyExceptionTest keeps compiling and
    // passing exactly as written — compensationRegistry defaults to null.
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine,
                           PolicyExceptionJpaRepository policyExceptionRepository) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, policyExceptionRepository, null);
    }

    // Task 33: fourth overload across Tasks 22/23/28/29/33 (4 -> 6 -> 7 -> 9 positional
    // parameters). A real code smell — a builder or a small dependencies-holder object would be
    // cleaner — but retrofitting one now means touching five already-green test files for a
    // purely cosmetic win. Recorded as a backlog item for Task 47's final engineering summary
    // ("replace WorkflowEngine's constructor overloads with a builder") rather than silently
    // living with it forever.
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine,
                           PolicyExceptionJpaRepository policyExceptionRepository,
                           CompensationRegistry compensationRegistry) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.policyEngine = policyEngine;
        this.policyExceptionRepository = policyExceptionRepository;
        this.compensationRegistry = compensationRegistry;
    }

    public String submit(String feature, List<StageDefinition> stages, String correlationId) {
        return submit(feature, stages, correlationId, null);
    }

    // Codex REQUEST AMENDMENTS follow-up (Section 17 review): the original 3-arg submit() (kept
    // above, delegating here with requirementText=null, for every caller — ScenarioRunner, every
    // existing test — that has no requirement text to carry) validated requirementText at the
    // controller and then dropped it entirely; it never reached contextJson or any persisted
    // column, so Stage 1's own requirement was never captured verbatim or with provenance,
    // contrary to REQ-011/the OpenAPI contract. Seeding it into the initial artifacts map here —
    // before runFrom() ever runs a stage — means it is genuinely part of the workflow context
    // from the very first batch onward: buildContext(instance) reads it back on every subsequent
    // resume, and it shows up in the first stage's own persisted inputsSnapshot
    // (contextArtifactsAtStageStart), inspectable via GET /workflows/{id} without any new API
    // surface.
    public String submit(String feature, List<StageDefinition> stages, String correlationId, String requirementText) {
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        String workflowId = UUID.randomUUID().toString();
        registry.put(workflowId, new StageRegistration(stages, graph));

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature(feature);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(correlationId);
        if (requirementText != null) {
            Map<String, Object> requirement = new LinkedHashMap<>();
            requirement.put("text", requirementText);
            requirement.put("submittedAt", Instant.now().toString());
            requirement.put("submittedVia", "POST /workflows");
            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put("requirement", requirement);
            instance.setContextJson(Map.of("decisions", List.of(), "artifacts", artifacts));
        }
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

    // Codex REQUEST AMENDMENTS item 7: every instance write below now goes through
    // applyInstanceUpdate — a short, independently-locked transaction that re-reads the instance
    // fresh, checks it isn't ALREADY terminal (a concurrent decision on a different sibling gate
    // may have already committed FAILED/COMPLETED), and abandons the write instead of overwriting
    // a terminal state back to something else. This is deliberately NOT one transaction wrapping
    // runFrom's whole body: an earlier version of this fix tried that, and broke cross-thread
    // visibility of runBatch's OWN worker-thread writes — Hibernate's first-level cache returned
    // the STALE step entity on a second read within the same enclosing transaction/session, since
    // those worker threads commit on separate connections/sessions runFrom's single session never
    // re-queries once an entity is already loaded. Keeping each read of workflow_steps its own
    // independent, uncached round trip (as it always was) while making ONLY the workflow_instances
    // write itself atomic-and-checked is what actually closes the race without breaking that.
    private void runFrom(String workflowId) {
        StageRegistration registration = registry.get(workflowId);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();

        // Cheap early exit: a concurrent decision may have already driven this workflow to a
        // terminal state before this call was even triggered. Not a substitute for the per-write
        // checks below (those are what actually close the race) — just avoids doing any work at
        // all for the common case where nothing has changed.
        if (instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.COMPLETED) {
            return;
        }

        int planVersion = instance.getPlanVersion();
        WorkflowContext ctx = buildContext(instance);

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            // Codex REQUEST AMENDMENTS item 7: re-check terminal status at the START of EVERY
            // batch iteration, not just once at entry. This closes a deeper gap the entry-only
            // guard and the per-write locking above didn't: the exit-gate scan below only checks
            // "has every exit-gated sibling in THIS batch been decided" — it does not (and can't,
            // by itself) know whether one of those decisions was a REJECTION that already failed
            // the whole workflow via a DIFFERENT thread's approve() call. That other thread's
            // rejection never calls runFrom() itself (outcome.rejected() short-circuits it), so
            // nothing stops THIS thread's runFrom() — reached via approving a DIFFERENT sibling —
            // from finding the exit gate satisfied ("all decided") and marching into a dependent
            // batch whose real dependency was rejected, not approved. A fresh read here, before
            // ever touching the next batch, is what actually closes it.
            WorkflowInstanceEntity freshInstance = instanceRepository.findById(workflowId).orElseThrow();
            if (freshInstance.getStatus() == WorkflowStatus.FAILED || freshInstance.getStatus() == WorkflowStatus.COMPLETED) {
                return;
            }

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
                        // Halt entirely either way: handler has NOT run, and if applyInstanceUpdate
                        // was abandoned because the workflow is already terminal, there is equally
                        // nothing further for this call to do.
                        applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.PENDING_APPROVAL));
                        return;
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
                try {
                    runBatch(workflowId, planVersion, pending, ctx, batchStartArtifacts);
                } catch (SafeStopException e) {
                    // The failing step's own SAFE_STOPPED status/safeStopReason/result were already
                    // persisted inside runStage before this propagated. The instance-level context
                    // (decisions/artifacts) is ALWAYS persisted here now — Codex REQUEST AMENDMENTS
                    // follow-up (Section 17 review) corrected a real defect in the previous
                    // POLICY_BLOCKED-only version: `ctx` is SHARED across every stage runBatch just
                    // dispatched, not owned by the one that failed, so restricting the persist to
                    // one specific reason discarded every OTHER (already-succeeded) sibling's
                    // artifacts in a mixed batch too — not just the failing stage's own. A mixed
                    // batch (e.g. one sibling POLICY_BLOCKED, one RETRY_EXHAUSTED) whose
                    // SafeStopException happened to come from the non-POLICY_BLOCKED sibling
                    // (Round 5's runBatch join order is unspecified) discarded the successful
                    // sibling's evidence, and a subsequent policy-boundary re-evaluation could then
                    // fail-closed on a policy that had genuinely PASSED on the original run —
                    // forcing an operator to grant an unnecessary exception just to unblock a
                    // legitimate workflow. Persisting unconditionally is safe: a RETRY_EXHAUSTED/
                    // COMPENSATION_FAILED/UNEXPECTED_FAILURE step's OWN handler will still
                    // genuinely re-run on resume (resetSafeStoppedStepToPending) and simply
                    // overwrites whatever artifact keys it itself owns — nothing reads a failed
                    // stage's own prior partial artifact to decide what to do on retry.
                    applyInstanceUpdate(workflowId, i -> {
                        i.setStatus(WorkflowStatus.SAFE_STOPPED);
                        i.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", ctx.artifacts()));
                    });
                    recordEvent(workflowId, "orchestrator", "workflow-engine", "safe_stop_triggered",
                            null, "SAFE_STOPPED", e.getMessage());
                    return;
                }

                if (!applyInstanceUpdate(workflowId,
                        i -> i.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", ctx.artifacts())))) {
                    return; // a concurrent decision already resolved this workflow terminally
                }
            }

            // Scoped re-review finding (post-claimStage follow-up): claimStage stops a stage's
            // handler from running twice, but on its own does nothing to stop the THREAD that
            // lost the claim from walking straight past this batch — pending was empty for it
            // (the only stage in it was already RUNNING, not PENDING, so the filter above dropped
            // it), so it fell through both the runBatch call above and would otherwise reach the
            // exit-gate scan and the next batch while the winner's stage was still executing (or,
            // worse, while the winner's stage had just SAFE_STOPPED and the winner's own
            // SafeStopException catch — which is what actually halts the workflow — hadn't run
            // yet, since that catch lives entirely in the WINNER's call stack and this thread
            // never goes through it). A fresh read here, of every stage in the FULL batch (not
            // just this call's `pending`), is what actually closes both: RUNNING means a sibling
            // is still executing; SAFE_STOPPED means a sibling just failed and the thread that
            // caused it — the only one that runs the SAFE_STOPPED-transition logic above — hasn't
            // necessarily finished doing so. Either way, this call is not the one that gets to
            // carry the workflow forward. WAITING_APPROVAL/SUCCEEDED/COMPENSATED are the outcomes
            // the exit-gate scan below is designed to handle and are safe to fall through to.
            List<StepStatus> siblingStatuses = batch.stream()
                    .map(s -> stepStatus(workflowId, planVersion, s.name()))
                    .toList();
            if (siblingStatuses.contains(StepStatus.RUNNING)) {
                return;
            }
            if (siblingStatuses.contains(StepStatus.SAFE_STOPPED)) {
                // Second scoped re-review finding: a mixed batch where ONE sibling is still
                // SAFE_STOPPED (e.g. POLICY_BLOCKED, awaiting grantPolicyException) while ANOTHER
                // sibling was reset by retrySafeStopped (e.g. RETRY_EXHAUSTED) and just re-ran
                // successfully leaves this thread here — correctly halted from advancing — but
                // the instance itself was set RUNNING by retrySafeStopped before this call, and
                // nothing re-asserts SAFE_STOPPED on it. Without this, retrySafeStopped's own
                // precondition ("workflow ... is not SAFE_STOPPED (status: RUNNING)") permanently
                // refuses a second call, even after the operator grants the missing exception —
                // an operationally unrecoverable workflow. applyInstanceUpdate is idempotent with
                // the winner's own SAFE_STOPPED write in the concurrent case (same target state)
                // and a no-op if the instance is already FAILED/COMPLETED.
                applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.SAFE_STOPPED));
                return;
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
                // Task 33 correction (independent-review finding): COMPENSATED is a real terminal
                // outcome a stage can now reach (retries exhausted, a registered compensation ran)
                // that didn't exist before this task. It satisfied neither SUCCEEDED nor
                // WAITING_APPROVAL, so an exit-gated stage that ended COMPENSATED silently bypassed
                // its own human-approval gate entirely — dependents proceeded without the review the
                // stage's config demands. Arguably a compensated failure needs that review MORE than
                // a clean success does (the original outcome never happened; only a fallback ran).
                boolean readyForGate = status == StepStatus.SUCCEEDED || status == StepStatus.COMPENSATED
                        || status == StepStatus.WAITING_APPROVAL;
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
                applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.PENDING_APPROVAL));
                return; // halt either way; handlers already ran and are WAITING_APPROVAL; approve() resumes
            }
        }

        // Codex REQUEST AMENDMENTS item 7: this is the write the concurrency test targets most
        // directly — a concurrent decision on a different sibling gate must never have this call
        // overwrite its (already-committed) FAILED status back to COMPLETED.
        if (!applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.COMPLETED))) {
            return; // a concurrent decision already resolved this workflow terminally
        }
        // actorType "orchestrator" (not "engine"): openapi.yaml's AuditEventResponse.actorType
        // enum is {agent, human, orchestrator} — the engine completing a workflow on its own
        // initiative is exactly the "orchestrator" case.
        recordEvent(workflowId, "orchestrator", "workflow-engine", "workflow_completed", null, "OK", null);
    }

    // Codex REQUEST AMENDMENTS item 7: applies a mutation to the workflow instance inside a short,
    // independently pessimistic-locked transaction — a fresh read, not the (possibly stale)
    // in-memory `instance` object callers in runFrom hold. Returns false (mutation abandoned,
    // nothing written) if the instance is already terminal (FAILED/COMPLETED) by the time the lock
    // is acquired; true if the mutation was applied and saved.
    private boolean applyInstanceUpdate(String workflowId, Consumer<WorkflowInstanceEntity> mutator) {
        Boolean applied = transactionTemplate.execute(status -> {
            WorkflowInstanceEntity fresh = instanceRepository.findWithLockById(workflowId).orElseThrow();
            if (fresh.getStatus() == WorkflowStatus.FAILED || fresh.getStatus() == WorkflowStatus.COMPLETED) {
                return Boolean.FALSE;
            }
            mutator.accept(fresh);
            instanceRepository.save(fresh);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(applied);
    }

    // Independent-review finding (post-item-7 follow-up): two concurrent runFrom() calls — e.g.
    // one thread per approve() call on DIFFERENT sibling exit gates in the same batch — each
    // independently recompute `pending` by reading step status, and both can observe the SAME
    // dependent stage as PENDING before either has dispatched it, double-executing its handler.
    // Every fix already in this file (applyInstanceUpdate, the per-iteration terminal re-check)
    // protects the WORKFLOW INSTANCE's status; none of them claim the STEP itself, which is what
    // this closes: only a thread that wins the atomic PENDING -> RUNNING transition below actually
    // runs the stage's handler. The loser sees a non-PENDING status and skips it as a no-op — the
    // winner's execution already covers it, and future.get() below only ever waits on the threads
    // that actually claimed a stage.
    private void runBatch(String workflowId, int planVersion, List<StageDefinition> pending, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        List<StageDefinition> claimed = pending.stream()
                .filter(stage -> claimStage(workflowId, planVersion, stage.name(), inputsSnapshot))
                .toList();
        if (claimed.isEmpty()) {
            return; // every stage in this batch was already claimed by a concurrent runFrom() call
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, claimed.size()));
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (StageDefinition stage : claimed) {
                futures.add(pool.submit(() -> runStage(workflowId, planVersion, stage, ctx, inputsSnapshot)));
            }
        } finally {
            pool.shutdown();
        }

        // Independent-review finding (5th pass, post-cd797f0 follow-up): await EVERY claimed
        // stage's future before this method returns or throws — even once one has already
        // failed. The previous form propagated the FIRST failure immediately (via future.get()
        // inside the loop, which throws on the first exception it meets), abandoning the
        // remaining futures unjoined. Their handlers kept running on the pool's threads
        // regardless — pool.shutdown() does not interrupt in-flight tasks — but nothing was
        // left to notice when they finished: every "is anything still RUNNING" check in
        // runFrom only ever runs inside a thread that returned from THIS method, and this
        // method had already returned. A batch with an orphaned sibling still executing could
        // then get "resolved" (e.g. by a retrySafeStopped call landing mid-orphan) with no
        // thread left to observe the orphan's eventual completion, permanently stranding the
        // instance RUNNING. Joining every future here — regardless of order or which one
        // fails first — guarantees that by the time this method returns, every stage IT
        // dispatched has actually finished, restoring the invariant runFrom's per-batch check
        // depends on.
        Throwable firstSafeStop = null;
        Throwable firstOther = null;
        boolean interrupted = false;
        for (Future<?> future : futures) {
            // Codex REQUEST AMENDMENTS follow-up (Section 17 review): an InterruptedException
            // from future.get() means THIS thread was interrupted while waiting, not that the
            // future itself failed — the handler it's waiting on is still running. Moving on to
            // the next future without re-awaiting this one is exactly the orphan-thread gap the
            // Round 5 fix (join every future unconditionally) was meant to close, just reachable
            // via a different trigger. Retry get() on the SAME future — java.util.concurrent
            // clears a thread's interrupt status the moment a blocking call consumes it, so the
            // retry does not spin or return immediately unless re-interrupted — and only restore
            // the interrupt flag once every future has actually been joined.
            boolean joined = false;
            while (!joined) {
                try {
                    future.get();
                    joined = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    // Task 28: a SafeStopException from a mandatory policy failure must propagate
                    // as itself — not wrapped in a generic RuntimeException — so runFrom's catch
                    // above can distinguish "safe-stop the workflow" from "an unexpected handler
                    // error."
                    Throwable cause = e.getCause();
                    if (cause instanceof SafeStopException && firstSafeStop == null) {
                        firstSafeStop = cause;
                    } else if (firstOther == null) {
                        firstOther = cause;
                    }
                    joined = true;
                }
            }
        }
        // Codex REQUEST AMENDMENTS follow-up (Section 17 review): restore the interrupt flag so a
        // caller that cares can observe it — but do NOT synthesize an exception purely because
        // this thread was interrupted while waiting. Every future was still joined to its REAL
        // outcome above (that's the whole point of retrying get() on the same future rather than
        // abandoning it), so if every stage actually succeeded, this method returning normally is
        // the correct behavior; throwing here would misrepresent a fully successful batch as
        // failed merely because the waiting thread was interrupted partway through.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (firstSafeStop != null) {
            throw (SafeStopException) firstSafeStop;
        }
        if (firstOther != null) {
            throw new RuntimeException(firstOther);
        }
    }

    // Atomically transitions a step PENDING -> RUNNING under the step's existing pessimistic row
    // lock (the same lock recordDecisionInTransaction uses), so exactly one concurrent caller
    // dispatches this stage's handler. Sets startedAt/inputsSnapshot here — the values a losing
    // caller would otherwise have raced to write — so runStage's own read afterward is guaranteed
    // fresh rather than reused from a stale in-memory reference.
    private boolean claimStage(String workflowId, int planVersion, String stageName,
                                Map<String, Object> inputsSnapshot) {
        Boolean claimed = transactionTemplate.execute(status -> {
            WorkflowStepEntity step = stepRepository
                    .findWithLockByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stageName)
                    .orElseThrow();
            if (step.getStatus() != StepStatus.PENDING) {
                return Boolean.FALSE;
            }
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(Instant.now());
            step.setInputsSnapshot(inputsSnapshot);
            stepRepository.save(step);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(claimed);
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
            // Codex REQUEST AMENDMENTS item 7: pessimistic row lock on the WORKFLOW instance
            // itself, acquired before the step-level lock below. This closes the previously
            // disclosed cross-sibling race: two DIFFERENT exit-gated siblings decided truly
            // simultaneously (reject one, approve the other) can no longer both pass the
            // terminal-status check below before either commits — the second caller now blocks
            // here until the first's transaction commits or rolls back, then re-reads the
            // now-committed instance status.
            WorkflowInstanceEntity instance = instanceRepository.findWithLockById(workflowId)
                    .orElseThrow(() -> new NoPendingApprovalException("unknown workflow: " + workflowId));

            // Round-6b correction (independent-review finding): a terminal workflow accepts no
            // further decisions.
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

            // Codex REQUEST AMENDMENTS item 8: an exit-gated step's post-approval status now
            // restores from preGateStatus (captured by enterPendingApproval right before it
            // overwrote the step to WAITING_APPROVAL) instead of hardcoding SUCCEEDED — a step
            // that reached this gate via COMPENSATED (Task 33) stays COMPENSATED through approval
            // or rejection alike, matching the pre-existing symmetry ("the handler's own
            // completion stands; only downstream is blocked via instance FAILED on rejection").
            // preGateStatus falls back to SUCCEEDED for rows persisted before this column existed.
            StepStatus exitGateOutcome = step.getPreGateStatus() != null ? step.getPreGateStatus() : StepStatus.SUCCEEDED;
            boolean rejected = "rejected".equals(decision);
            if (rejected) {
                step.setStatus(stage.entryApproval() ? StepStatus.FAILED : exitGateOutcome);
            } else {
                step.setStatus(stage.entryApproval() ? StepStatus.PENDING : exitGateOutcome);
            }
            step.setPreGateStatus(null);
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
        // Codex REQUEST AMENDMENTS item 8: capture the status a gate is about to overwrite, so
        // approve() can restore it afterward instead of hardcoding SUCCEEDED — the only caller of
        // this method with anything other than PENDING/SUCCEEDED as the pre-gate status is an
        // exit-gated step that reached COMPENSATED (Task 33), which must stay COMPENSATED through
        // approval, not silently read as a clean success.
        step.setPreGateStatus(step.getStatus());
        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);
    }

    private void runStage(String workflowId, int planVersion, StageDefinition stage, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        // status/startedAt/inputsSnapshot are already committed here — claimStage (in runBatch)
        // performed the atomic PENDING -> RUNNING transition before this call was ever dispatched.
        // A fresh read, not a reused reference, so this reflects that committed state.
        WorkflowStepEntity step = requireStep(workflowId, planVersion, stage.name());

        // Codex REQUEST AMENDMENTS item 5: actually wire T030's RetryExecutor (bounded exponential
        // backoff) around T031's TimeoutExecutor, rather than a hand-rolled loop that never slept
        // between attempts. Classification comes from the handler itself: PermanentStageException
        // never retries; TransientStageException — which StageTimeoutException now IS, by
        // inheritance — retries up to stage.maxRetries() additional attempts, with real
        // exponential backoff between them. onRetry fires only when a retry is actually about to
        // happen (never on the final exhausting failure), so step.retryCount keeps tracking
        // retries actually PERFORMED, bounded by maxRetries — same semantics as before, now sitting
        // on the real T030 backoff/retry machinery instead of duplicating its math inline. Every
        // existing caller is unaffected: StageDefinition.of(...) defaults maxRetries=0/
        // timeoutSeconds=30, so a normal non-throwing handler still runs exactly once, inside a 30s
        // timeout window it never approaches, and proceeds through the unchanged policy-check/
        // SUCCEEDED path below.
        Map<String, Object> result = null;
        boolean succeeded = false;
        SafeStopReason failureReason = SafeStopReason.RETRY_EXHAUSTED;
        StageHandler timeoutBounded = TimeoutExecutor.withTimeout(stage.handler(), stage.timeoutSeconds() * 1000L);
        StageHandler retryable = RetryExecutor.withBoundedRetry(timeoutBounded, stage.maxRetries(),
                RETRY_BACKOFF_BASE_MILLIS, attempt -> {
                    step.setRetryCount(step.getRetryCount() + 1);
                    stepRepository.save(step);
                });
        try {
            result = retryable.run(ctx);
            succeeded = true;
        } catch (PermanentStageException e) {
            // never retried; falls through to the exhaustion/compensation path below
        } catch (TransientStageException e) {
            // retries exhausted (a plain transient failure or a StageTimeoutException — now the
            // same catch clause since StageTimeoutException extends TransientStageException)
        } catch (RuntimeException e) {
            // Codex REQUEST AMENDMENTS follow-up (Section 17 review): neither RetryExecutor nor
            // TimeoutExecutor catches anything outside the Permanent/TransientStageException
            // hierarchy — an unexpected handler bug (NPE, IllegalStateException, etc.) previously
            // propagated straight out of runStage uncaught, leaving the step at RUNNING (set by
            // claimStage before the handler ran) and the workflow instance untouched forever: no
            // terminal write, no SafeStopException for runFrom's catch to react to. Fail-closed:
            // this falls into the same exhaustion/compensation path below a genuinely exhausted
            // retry takes, tagged with its own reason so the audit trail and retrySafeStopped's
            // resume logic (which already treats every non-POLICY_BLOCKED reason identically —
            // reset and genuinely re-run) can tell an unexpected bug apart from ordinary
            // exhaustion without any further branching needed there.
            failureReason = SafeStopReason.UNEXPECTED_FAILURE;
        }

        if (!succeeded) {
            // Design note (brief, Task 33): a compensated failure does NOT throw — the batch and
            // the workflow proceed normally toward COMPLETED. Compensation is "the failure was
            // handled," a controlled saga-style continuation, not a block. Only an UNCOMPENSATED
            // exhaustion, or the compensation itself failing, throws SafeStopException, matching
            // Task 28's policy-failure path.
            boolean compensated = false;
            if (compensationRegistry != null) {
                try {
                    compensated = compensationRegistry.run(stage.name(), result != null ? result : Map.of());
                } catch (RuntimeException compensationFailure) {
                    // Codex REQUEST AMENDMENTS item 2: a compensation function that itself throws
                    // must not corrupt state or leave the workflow stuck RUNNING. Persist the same
                    // endedAt/result bookkeeping every other terminal path gets, mark the step
                    // SAFE_STOPPED, emit an audit event naming the failure, and propagate
                    // SafeStopException so runFrom's existing catch transitions the whole workflow
                    // to SAFE_STOPPED — the same mechanism every other safe-stop cause already uses.
                    step.setEndedAt(Instant.now());
                    step.setResult(result != null ? result : Map.of());
                    step.setStatus(StepStatus.SAFE_STOPPED);
                    step.setSafeStopReason(SafeStopReason.COMPENSATION_FAILED);
                    stepRepository.save(step);
                    recordEvent(workflowId, "orchestrator", "workflow-engine", "compensation_failed:" + stage.name(),
                            stage.name(), "FAILED", compensationFailure.getMessage());
                    throw new SafeStopException(
                            "compensation itself failed for stage " + stage.name() + ": " + compensationFailure.getMessage(),
                            SafeStopReason.COMPENSATION_FAILED);
                }
            }
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            if (compensated) {
                step.setStatus(StepStatus.COMPENSATED);
                stepRepository.save(step);
                // actorType "orchestrator": the engine deciding to run a compensation on the
                // stage's behalf, not the stage's own agent-attributed action.
                recordEvent(workflowId, "orchestrator", "workflow-engine", "compensation_ran:" + stage.name(),
                        stage.name(), "OK", null);
                return; // handled: this batch and the workflow continue normally
            }
            step.setStatus(StepStatus.SAFE_STOPPED);
            step.setSafeStopReason(failureReason);
            stepRepository.save(step);
            throw new SafeStopException("stage " + stage.name() + " did not succeed and no compensation was"
                    + " registered (or compensation was not applicable): " + failureReason, failureReason);
        }

        // Task 28: policy enforcement runs right after the handler returns, before the step is
        // marked SUCCEEDED — a mandatory FAIL must be able to preempt success, not follow it.
        // Skipped entirely when no PolicyEngine is configured (the shared production bean today).
        if (policyEngine != null) {
            List<PolicyCheckResult> policyResults = policyEngine.evaluate(stage.name(), ctx.artifacts());
            boolean mandatoryFail = false;
            for (PolicyCheckResult pr : policyResults) {
                PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
                evaluation.setWorkflowId(workflowId);
                evaluation.setStageName(stage.name());
                evaluation.setPolicyId(pr.policyId());
                evaluation.setPolicyVersion(policyEngine.version());
                evaluation.setOutcome(pr.outcome());
                evaluation.setReason(pr.reason());
                policyEvaluationRepository.save(evaluation);
                // A mandatory FAIL blocks unless an approved, unexpired exception covers this
                // policy — see hasApprovedUnexpiredException below. Codex REQUEST AMENDMENTS item
                // 3: EXCEPTION_REQUESTED blocks the same way FAIL does — it is still not a PASS,
                // just a differently-labeled failure the audit trail can distinguish as "the stage
                // explicitly flagged this, awaiting a decision" rather than a silent gap. An
                // approved exception excuses either outcome identically.
                if (pr.mandatory()
                        && (pr.outcome() == PolicyOutcome.FAIL || pr.outcome() == PolicyOutcome.EXCEPTION_REQUESTED)
                        && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                    mandatoryFail = true;
                }
            }
            if (mandatoryFail) {
                step.setStatus(StepStatus.SAFE_STOPPED);
                step.setSafeStopReason(SafeStopReason.POLICY_BLOCKED);
                step.setEndedAt(Instant.now());
                step.setResult(result != null ? result : Map.of());
                stepRepository.save(step);
                throw new SafeStopException("mandatory policy failure at stage " + stage.name(),
                        SafeStopReason.POLICY_BLOCKED);
            }
        }

        step.setStatus(StepStatus.SUCCEEDED);
        step.setEndedAt(Instant.now());
        step.setResult(result != null ? result : Map.of());
        stepRepository.save(step);
        recordEvent(workflowId, "agent", stage.name(), "stage_succeeded:" + stage.name(), stage.name(), "OK", null);
    }

    // Task 29: a mandatory FAIL is excused only by an exception that is BOTH recorded for this
    // exact (workflowId, policyId) pair AND not yet expired — a granted-but-expired exception
    // never silently resurrects (REQ-013-adjacent: approval is never inferred from something that
    // has lapsed). When no PolicyExceptionJpaRepository is configured (any caller still using the
    // Task 28-era 5/7-arg constructors), this stays the original always-false placeholder.
    private boolean hasApprovedUnexpiredException(String workflowId, String policyId) {
        if (policyExceptionRepository == null) return false;
        return policyExceptionRepository.findByWorkflowIdAndPolicyId(workflowId, policyId).stream()
                .anyMatch(exc -> exc.getExpiry().isAfter(Instant.now()));
    }

    // Task 29: records a human-approved, time-bounded deviation from a mandatory policy. Does NOT
    // itself resume a safe-stopped workflow — callers combine this with retrySafeStopped(...) (or
    // grant it ahead of time, before the stage that would otherwise fail ever runs).
    public void grantPolicyException(String workflowId, String policyId, String reason, String scope,
                                      String approvingAuthority, String compensatingControl, Instant expiry) {
        // Codex REQUEST AMENDMENTS item 4: validate before touching any state — same discipline
        // approve() already applies to its own inputs. Every field here maps directly to a
        // data-model.md-approved NOT NULL column (see PolicyExceptionEntity); this is the
        // application-level enforcement that guarantees this engine's own insert path can never
        // violate what the schema already requires.
        if (workflowId == null || !instanceRepository.existsById(workflowId)) {
            throw new InvalidPolicyExceptionRequestException("unknown workflow: " + workflowId);
        }
        if (policyId == null || policyId.isBlank()) {
            throw new InvalidPolicyExceptionRequestException("policyId must be present and non-blank");
        }
        if (policyEngine != null && !policyEngine.hasPolicy(policyId)) {
            throw new InvalidPolicyExceptionRequestException("unknown policy: " + policyId);
        }
        requireNonBlank(reason, "reason");
        requireNonBlank(scope, "scope");
        requireNonBlank(approvingAuthority, "approvingAuthority");
        requireNonBlank(compensatingControl, "compensatingControl");
        if (expiry == null) {
            throw new InvalidPolicyExceptionRequestException("expiry must be present");
        }
        if (!expiry.isAfter(Instant.now())) {
            throw new InvalidPolicyExceptionRequestException(
                    "expiry must be in the future, got: " + expiry + " (an exception is never granted already-expired)");
        }

        PolicyExceptionEntity exception = new PolicyExceptionEntity();
        exception.setWorkflowId(workflowId);
        exception.setPolicyId(policyId);
        exception.setReason(reason);
        exception.setScope(scope);
        exception.setApprovingAuthority(approvingAuthority);
        exception.setCompensatingControl(compensatingControl);
        exception.setApprovedAt(Instant.now());
        exception.setExpiry(expiry);
        policyExceptionRepository.save(exception);
        recordEvent(workflowId, "human", approvingAuthority, "policy_exception_granted:" + policyId,
                policyId, "OK", reason);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPolicyExceptionRequestException(fieldName + " must be present and non-blank");
        }
    }

    // Task 29: resets every SAFE_STOPPED step in the workflow's CURRENT plan version back to
    // PENDING and resumes runFrom() — no policy-recomputation logic is duplicated here. The
    // stage's handler already ran once (during the safe-stopped attempt) and will simply run
    // again; hasApprovedUnexpiredException now sees the just-granted exception, so the same
    // mandatory FAIL no longer blocks. If a DIFFERENT mandatory policy still fails with no
    // exception, the stage safe-stops again — same as any first attempt.
    //
    // Review correction (independent review): this method requires the workflow to actually be
    // SAFE_STOPPED before doing anything. An earlier version of this comment claimed unconditionally
    // resetting instance.status to RUNNING was harmless even with zero SAFE_STOPPED steps — that
    // claim was false in two reachable states: a FAILED workflow (a rejected entry-gate approval
    // left some step FAILED, not PENDING) would be resurrected to RUNNING and runFrom would silently
    // skip that step's batch and execute downstream stages that depend on a stage that never
    // actually succeeded; a PENDING_APPROVAL workflow with an unrelated entry-gated step still
    // WAITING_APPROVAL would similarly resurrect to RUNNING and runFrom could execute downstream
    // batches before that pending approval decision is ever made — an approval-gate bypass. Only the
    // exit-gate-pending case was genuinely idempotent as originally claimed.
    public void retrySafeStopped(String workflowId) {
        // Codex REQUEST AMENDMENTS follow-up (Section 17 review): the SAFE_STOPPED -> RUNNING
        // transition below is now claimed atomically under the instance's existing pessimistic
        // row lock, exactly like claimStage does for a step's PENDING -> RUNNING transition.
        // Before this, two concurrent retrySafeStopped(workflowId) calls could both read the
        // instance as SAFE_STOPPED (unlocked), both read the SAME steps list (unlocked), and both
        // proceed to reset the same non-POLICY_BLOCKED step — caller A's reset could be claimed
        // and run to completion by claimStage/runStage, then caller B's stale in-memory
        // WorkflowStepEntity (read before A's write) could still get saved back with status
        // PENDING and every OTHER field frozen at its pre-A values, undoing A's already-committed
        // progress and re-arming the step for a second execution. Claiming the instance FIRST
        // means only one caller ever reaches the per-step work below; a second concurrent caller
        // blocks on the instance lock and, once it acquires it, observes RUNNING (not
        // SAFE_STOPPED) and is rejected exactly like any other "not actually safe-stopped" call —
        // no step-level race is reachable at all.
        if (!claimSafeStoppedForRetry(workflowId)) {
            WorkflowInstanceEntity current = instanceRepository.findById(workflowId).orElseThrow();
            throw new IllegalStateException(
                    "workflow " + workflowId + " is not SAFE_STOPPED (status: " + current.getStatus() + ")");
        }
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();
        StageRegistration registration = registry.get(workflowId);
        List<WorkflowStepEntity> steps = stepRepository.findByWorkflowIdAndPlanVersionOrderBySequence(workflowId, planVersion);

        // Codex REQUEST AMENDMENTS item 6: a POLICY_BLOCKED step's handler already ran to
        // completion once — re-running it would risk double-executing whatever real side effect it
        // had. Resolve ONLY the policy boundary against the persisted (post-handler) context,
        // never touching stage.handler() again. A RETRY_EXHAUSTED, COMPENSATION_FAILED, or
        // UNEXPECTED_FAILURE step never actually completed, so it genuinely needs to run again —
        // reset to PENDING as before, letting runFrom's normal machinery re-execute it.
        WorkflowContext ctx = buildContext(instance);
        boolean anyProgress = false;
        for (WorkflowStepEntity step : steps) {
            if (step.getStatus() != StepStatus.SAFE_STOPPED) continue;
            if (step.getSafeStopReason() == SafeStopReason.POLICY_BLOCKED) {
                StageDefinition stage = registration.stages().stream()
                        .filter(s -> s.name().equals(step.getName())).findFirst().orElseThrow();
                if (reevaluatePolicyBoundary(workflowId, step, stage, ctx)) {
                    anyProgress = true;
                }
            } else if (resetSafeStoppedStepToPending(workflowId, planVersion, step.getName())) {
                anyProgress = true;
            }
        }
        if (!anyProgress) {
            // Nothing actually changed — every SAFE_STOPPED step is still policy-blocked with no
            // (newly) approved exception. Reassert SAFE_STOPPED (this call's own claim above
            // already moved the instance to RUNNING) rather than leaving it resurrected to RUNNING
            // for no reason and letting runFrom silently march past a batch that never actually
            // cleared (mandatoryFail returning without throwing would otherwise be invisible to
            // runFrom's batch loop, which only reacts to a thrown SafeStopException).
            applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.SAFE_STOPPED));
            return;
        }
        runFrom(workflowId);
    }

    // Atomically transitions the workflow instance SAFE_STOPPED -> RUNNING under its existing
    // pessimistic row lock — the same lock/helper shape as applyInstanceUpdate, specialized to a
    // compare-and-set so only ONE concurrent retrySafeStopped(workflowId) caller ever proceeds
    // past this point.
    private boolean claimSafeStoppedForRetry(String workflowId) {
        Boolean claimed = transactionTemplate.execute(status -> {
            WorkflowInstanceEntity fresh = instanceRepository.findWithLockById(workflowId).orElseThrow();
            if (fresh.getStatus() != WorkflowStatus.SAFE_STOPPED) {
                return Boolean.FALSE;
            }
            fresh.setStatus(WorkflowStatus.RUNNING);
            instanceRepository.save(fresh);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(claimed);
    }

    // Atomically transitions a SAFE_STOPPED (non-POLICY_BLOCKED) step to PENDING under the step's
    // existing pessimistic row lock — the same shape as claimStage, applied to the opposite
    // transition. A fresh, locked read-check-write instead of retrySafeStopped's caller reusing
    // its own earlier unlocked list read is what actually closes the stale-object-save race
    // described above; retrySafeStopped's own instance-level claim already ensures only one
    // caller reaches this per-workflow, but locking each step individually too costs nothing and
    // keeps this helper correct standing alone, matching claimStage's own defense-in-depth.
    private boolean resetSafeStoppedStepToPending(String workflowId, int planVersion, String stepName) {
        Boolean reset = transactionTemplate.execute(status -> {
            WorkflowStepEntity step = stepRepository
                    .findWithLockByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName)
                    .orElseThrow();
            if (step.getStatus() != StepStatus.SAFE_STOPPED || step.getSafeStopReason() == SafeStopReason.POLICY_BLOCKED) {
                return Boolean.FALSE;
            }
            step.setStatus(StepStatus.PENDING);
            step.setSafeStopReason(null);
            stepRepository.save(step);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(reset);
    }

    // Codex REQUEST AMENDMENTS item 6: re-runs ONLY the policy check for a step whose handler
    // already succeeded, against the SAME context it succeeded against — never calls
    // stage.handler() again. Returns whether the boundary actually cleared (the step transitioned
    // to SUCCEEDED); if still blocked, the step is left exactly as it was (SAFE_STOPPED,
    // POLICY_BLOCKED) and this returns false.
    private boolean reevaluatePolicyBoundary(String workflowId, WorkflowStepEntity step, StageDefinition stage,
                                              WorkflowContext ctx) {
        List<PolicyCheckResult> policyResults = policyEngine.evaluate(stage.name(), ctx.artifacts());
        boolean mandatoryFail = false;
        for (PolicyCheckResult pr : policyResults) {
            PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
            evaluation.setWorkflowId(workflowId);
            evaluation.setStageName(stage.name());
            evaluation.setPolicyId(pr.policyId());
            evaluation.setPolicyVersion(policyEngine.version());
            evaluation.setOutcome(pr.outcome());
            evaluation.setReason(pr.reason());
            policyEvaluationRepository.save(evaluation);
            if (pr.mandatory()
                    && (pr.outcome() == PolicyOutcome.FAIL || pr.outcome() == PolicyOutcome.EXCEPTION_REQUESTED)
                    && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                mandatoryFail = true;
            }
        }
        if (mandatoryFail) {
            return false; // still blocked; step stays exactly as it was
        }
        step.setStatus(StepStatus.SUCCEEDED);
        step.setSafeStopReason(null);
        stepRepository.save(step);
        recordEvent(workflowId, "orchestrator", "workflow-engine", "policy_boundary_cleared:" + stage.name(),
                stage.name(), "OK", null);
        return true;
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

    // Extracted from runFrom (Codex REQUEST AMENDMENTS item 6): reused by retrySafeStopped/
    // reevaluatePolicyBoundary so a POLICY_BLOCKED resume reconstructs the exact same context
    // shape runFrom itself uses, from the same persisted contextJson.
    @SuppressWarnings("unchecked")
    private WorkflowContext buildContext(WorkflowInstanceEntity instance) {
        List<Map<String, Object>> priorDecisions =
                (List<Map<String, Object>>) instance.getContextJson().getOrDefault("decisions", new ArrayList<Map<String,Object>>());
        Map<String, Object> priorArtifacts =
                (Map<String, Object>) instance.getContextJson().getOrDefault("artifacts", new HashMap<String,Object>());
        return new WorkflowContext(instance.getCorrelationId(), priorDecisions, priorArtifacts);
    }

    private record StageRegistration(List<StageDefinition> stages, DependencyGraph graph) { }
}
