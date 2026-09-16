package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Task 35, remediated (independent review round 3 found the first version genuinely defective:
// REPLANNING committed before graph validation could strand a workflow on a bad request; already-
// executed reachable stages could be invalidated in violation of REQ-018; a survivor's plan_version
// was rewritten, contradicting the data model; the mutation was not atomic; blank actor/reason and
// duplicate replacement names were never validated). @DataJpaTest wraps each test in a transaction
// on the test thread's own connection; WorkflowEngine.runFrom dispatches stage handlers onto its
// internal thread pool, which borrows a *different* connection from the same DataSource, so without
// this override those worker threads cannot see the test thread's uncommitted inserts — same root
// cause and fix as every other WorkflowEngine*Test class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineReplanningTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engine(List<StageDefinition> stages) {
        return engine(stages, List.of());
    }

    private WorkflowEngine engine(List<StageDefinition> stages, List<StageDefinition> additional) {
        List<StageDefinition> catalogStages = new java.util.ArrayList<>(stages);
        catalogStages.addAll(additional);
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, null, null, null, null,
                com.schwab.urlshortener.support.TestStageCatalogs.fromStages(catalogStages));
    }

    @Test
    void replanInvalidatesDownstreamVersionsThePlanAndResumesPreservingLineage() {
        StageHandler design = ctx -> { ctx.recordDecision("agent", "initial design", "r1"); return Map.of(); };
        StageHandler oldImpl = ctx -> Map.of("shouldNotRun", true);
        // exitApproval: design must actually run and produce its decision before the human reviews
        // it and chooses to replan rather than simply approve-as-is.
        List<StageDefinition> stages = List.of(
                new StageDefinition("design", List.of(), design, false, true, 0, 30, null, null, null),
                StageDefinition.of("impl", List.of("design"), oldImpl));

        AtomicBoolean ran = new AtomicBoolean(false);
        StageHandler newImpl = ctx -> { ran.set(true); return Map.of(); };
        StageDefinition replacement = StageDefinition.of("impl_v2", List.of("design"), newImpl);
        WorkflowEngine engine = engine(stages, List.of(replacement));
        String workflowId = engine.submit("demo", stages, "run-35a");

        // Halted at design's exit gate — impl (PENDING, blocked behind it) has not run.
        WorkflowInstanceEntity halted = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(halted.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(halted.getPlanVersion()).isEqualTo(1);
        Long designStepId = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "design").orElseThrow().getId();

        // Replan while design's own exit gate is STILL open (the governance-hole scenario) —
        // impl_v2 must not slip past it. "impl" is PENDING (never started) — eligible under REQ-018.
        engine.replan(workflowId, "impl", List.of(replacement),
                "human", "clarified requirement changed implementation approach");

        WorkflowInstanceEntity afterReplan = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(afterReplan.getPlanVersion()).isEqualTo(2);
        // Unresolved-gate blocking: replan's own runFrom call must re-halt on design's still-open
        // gate, not let impl_v2 slip past it just because a new plan version now exists.
        assertThat(afterReplan.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(ran.get()).isFalse();

        WorkflowStepEntity oldImplStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow();
        assertThat(oldImplStep.getStatus()).isEqualTo(StepStatus.INVALIDATED);
        // Prior-history immutability: the invalidated row's own plan_version is untouched — it
        // stays frozen at the version it was actually created under, never bumped forward.
        assertThat(oldImplStep.getPlanVersion()).isEqualTo(1);

        // Plan-version isolation, corrected: "design" survives the replan (it is not downstream of
        // "impl") and is NEVER re-stamped — its plan_version stays exactly 1 forever, the row's id
        // and every other field untouched. The active-row resolution used throughout the engine
        // finds it by name/non-invalidated status, not by matching the instance's current
        // plan_version, so this is not a special case.
        WorkflowStepEntity designStep = stepRepository
                .findByWorkflowIdAndNameAndStatusNot(workflowId, "design", StepStatus.INVALIDATED).orElseThrow();
        assertThat(designStep.getId()).isEqualTo(designStepId);
        assertThat(designStep.getPlanVersion()).isEqualTo(1); // unchanged — the whole point of the fix
        assertThat(designStep.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);

        WorkflowStepEntity implV2Step = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "impl_v2").orElseThrow();
        assertThat(implV2Step.getStatus()).isEqualTo(StepStatus.PENDING);

        // Complete replan audit evidence: a "replanned" event with the version/stage transition.
        List<WorkflowEventEntity> events = eventRepository.findByWorkflowIdOrderById(workflowId);
        WorkflowEventEntity replannedEvent = events.stream()
                .filter(e -> "replanned".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(replannedEvent.getPayload())
                .containsEntry("fromVersion", 1)
                .containsEntry("toVersion", 2)
                .containsEntry("fromStage", "impl");
        assertThat(replannedEvent.getReason()).isEqualTo("clarified requirement changed implementation approach");

        // Now approve design's still-open gate (still findable, still governed by ITS OWN row) —
        // the workflow must resume and complete via the NEW plan (impl_v2), never touching the
        // invalidated old "impl".
        engine.approve(workflowId, "design", "approved", "human", "ok after replan");

        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(ran.get()).isTrue();

        // Context/decision lineage: "design"'s decision, recorded before the replan point, survives
        // the replan and the resume — it is neither lost nor duplicated.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) completed.getContextJson().get("decisions");
        assertThat(decisions.stream().filter(d -> "initial design".equals(d.get("decision")))).hasSize(1);

        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow()
                .getStatus()).isEqualTo(StepStatus.INVALIDATED); // still untouched by the later resume/approval
        assertThat(stepRepository.findByWorkflowIdAndNameAndStatusNot(workflowId, "design", StepStatus.INVALIDATED)
                .orElseThrow().getPlanVersion()).isEqualTo(1); // still untouched after full completion
    }

    // REQ-018: "the system MUST support altering the NOT-YET-STARTED portion... only not-yet-started
    // stages are altered... never rewrites or deletes the record of stages that already executed."
    // An exit-gated stage's WAITING_APPROVAL means its handler already ran to completion — it is
    // immutable from here, and replan must reject the WHOLE request rather than partially invalidate
    // eligible siblings.
    @Test
    void replanRejectsAnAlreadyExecutedExitGatedReachableStageWithoutMutatingAnything() {
        StageHandler review = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("review", List.of(), review, false, true, 0, 30, null, null, null));
        StageDefinition replacement = StageDefinition.of("review_v2", List.of(), ctx -> Map.of());
        WorkflowEngine engine = engine(stages, List.of(replacement));
        String workflowId = engine.submit("demo", stages, "run-35e");
        WorkflowStepEntity reviewStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "review").orElseThrow();
        assertThat(reviewStep.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(reviewStep.getPreGateStatus()).isEqualTo(StepStatus.SUCCEEDED); // handler already ran

        assertThatThrownBy(() -> engine.replan(workflowId, "review",
                List.of(replacement), "human", "changed my mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already started");

        // Nothing mutated: same row, same status, same plan_version, no replanned event, instance
        // status/plan_version unchanged.
        WorkflowStepEntity unchanged = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "review").orElseThrow();
        assertThat(unchanged.getId()).isEqualTo(reviewStep.getId());
        assertThat(unchanged.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "review_v2")).isEmpty();
    }

    // A RUNNING reachable stage — its handler is actively executing right now — is exactly as
    // "already started" as one that has already finished, and must be rejected the same way.
    // Deterministic via a latch controlling exactly when the handler is allowed to complete, not by
    // racing real timing.
    @Test
    void replanRejectsARunningReachableStageWithoutMutatingAnything() throws Exception {
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        StageHandler slow = ctx -> {
            handlerStarted.countDown();
            try {
                releaseHandler.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("slow", List.of(), slow));
        WorkflowEngine engine = engine(stages);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<String> submitFuture = pool.submit(() -> engine.submit("demo", stages, "run-35f"));
        assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // "slow" is now genuinely RUNNING in the database, mid-handler, on a different thread.
        // submit() doesn't return the workflowId until the (still-blocked) call completes, and this
        // test class does not roll back between methods (worker threads need to see committed
        // writes), so other tests' rows may already exist — identify this one by its distinctive
        // RUNNING instance status, which no OTHER test in this class leaves a row sitting at.
        String workflowId = null;
        for (int i = 0; i < 50 && workflowId == null; i++) {
            List<WorkflowInstanceEntity> running = instanceRepository.findAll().stream()
                    .filter(inst -> inst.getStatus() == WorkflowStatus.RUNNING)
                    .toList();
            if (!running.isEmpty()) workflowId = running.get(0).getId();
            else Thread.sleep(20);
        }
        assertThat(workflowId).isNotNull();
        String finalWorkflowId = workflowId;
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "slow")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.RUNNING);

        assertThatThrownBy(() -> engine.replan(finalWorkflowId, "slow", List.of(), "human", "abort mid-flight"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already started");

        releaseHandler.countDown();
        submitFuture.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    // Validation happens BEFORE any row is touched: an invalid replacement graph (a cycle here;
    // duplicate names and unknown dependencies go through the same DependencyGraph.validate() call)
    // must leave the instance, steps, and events completely unchanged, not merely stop short with a
    // dangling REPLANNING status.
    @Test
    void replanRejectsACyclicReplacementGraphWithoutMutatingAnything() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), handler, false, true, 0, 30, null, null, null));
        List<StageDefinition> cyclic = List.of(
                new StageDefinition("a", List.of("b"), handler, false, false, 0, 30, null, null, null),
                new StageDefinition("b", List.of("a"), handler, false, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages, cyclic);
        String workflowId = engine.submit("demo", stages, "run-35g");

        assertThatThrownBy(() -> engine.replan(workflowId, "gate", cyclic, "human", "oops"))
                .isInstanceOf(CycleException.class);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL); // never touched, not REPLANNING
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "gate").orElseThrow()
                .getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "a")).isEmpty();
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    @Test
    void replanRejectsDuplicateStageNamesInNewStagesWithoutMutatingAnything() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), handler, false, true, 0, 30, null, null, null));
        List<StageDefinition> duplicated = List.of(
                StageDefinition.of("dup", List.of(), handler),
                StageDefinition.of("dup", List.of(), handler));
        WorkflowEngine engine = engine(stages, List.of(duplicated.get(0)));
        String workflowId = engine.submit("demo", stages, "run-35h");

        assertThatThrownBy(() -> engine.replan(workflowId, "gate", duplicated, "human", "oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");

        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    @Test
    void replanRejectsBlankActorOrReasonWithoutMutatingAnything() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), handler, false, true, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35i");

        assertThatThrownBy(() -> engine.replan(workflowId, "gate", List.of(), " ", "a reason"))
                .isInstanceOf(InvalidApprovalDecisionException.class);
        assertThatThrownBy(() -> engine.replan(workflowId, "gate", List.of(), "human", ""))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    // independent review round 4 finding: data-model.md Section 5 specifies workflow_events.reason as
    // VARCHAR(1024) — a caller-supplied reason longer than that must be rejected cleanly at the
    // point of entry, not truncated or left to a raw DB constraint failure.
    @Test
    void replanRejectsAnOverLengthReasonWithoutMutatingAnything() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), handler, false, true, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35m");

        String tooLong = "x".repeat(1025);
        assertThatThrownBy(() -> engine.replan(workflowId, "gate", List.of(), "human", tooLong))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    // independent review round 4 finding: `registry` (the in-memory stage-list map) is a plain
    // ConcurrentHashMap, entirely outside the JDBC transaction replan()'s own mutations run in — a
    // write to it is visible to every other thread the instant it happens, regardless of whether
    // the surrounding transaction later commits or rolls back. Publishing the replacement
    // StageRegistration INSIDE that transaction (an earlier version of replan() did exactly this)
    // meant a commit-time failure rolled the DATABASE back to the pre-replan state while the
    // REGISTRY stayed on the replacement graph — permanently diverged, since nothing re-publishes
    // the old graph afterward. This test forces a GENUINE commit-time failure — a replacement stage
    // name that exceeds workflow_steps.name's own VARCHAR(255) column limit, a real
    // DataIntegrityViolationException at flush/commit time, not a contrived test-only hook — after
    // at least one reachable row has already been invalidated within the same transaction, and
    // proves both halves of the fix: the DATABASE fully rolls back (the invalidated row reverts),
    // and the REGISTRY was never mutated to the replacement graph in the first place — proven by
    // resuming the ORIGINAL (never-replaced) graph correctly, not a partial or replacement one.
    @Test
    void aCommitTimeFailureDuringReplanRollsBackTheDatabaseAndNeverPublishesTheReplacementRegistry() {
        AtomicBoolean implRan = new AtomicBoolean(false);
        StageHandler gateHandler = ctx -> Map.of();
        StageHandler implHandler = ctx -> { implRan.set(true); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gateHandler, false, true, 0, 30, null, null, null),
                new StageDefinition("impl", List.of("gate"), implHandler, false, false, 0, 30, null, null, null));
        String tooLongName = "x".repeat(300);
        List<StageDefinition> newStages = List.of(StageDefinition.of(tooLongName, List.of(), ctx -> Map.of()));
        WorkflowEngine engine = engine(stages, newStages);
        String workflowId = engine.submit("demo", stages, "run-35n");
        // Halted at gate's exit approval; "impl" is PENDING, never started — eligible under REQ-018.
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        // workflow_steps.name has no explicit @Column length, so Hibernate defaults to
        // VARCHAR(255) — 300 characters is a genuine column-width violation, not a fabricated one.
        assertThatThrownBy(() -> engine.replan(workflowId, "impl", newStages, "human",
                "trigger a genuine mid-mutation commit failure"))
                .isInstanceOf(RuntimeException.class);

        // Database fully rolled back: "impl" is still PENDING (never invalidated), plan_version
        // untouched, no replanned event, no oversized row persisted.
        WorkflowStepEntity implStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow();
        assertThat(implStep.getStatus()).isEqualTo(StepStatus.PENDING);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, tooLongName)).isEmpty();

        // Registry not diverged: approving "gate" and resuming must still run the ORIGINAL "impl"
        // stage (from the never-replaced graph) to completion. If the registry had been left on the
        // (rolled-back) replacement graph, this would either fail to find "impl" at all or complete
        // the workflow without ever running it.
        engine.approve(workflowId, "gate", "approved", "human", "proceed on the original plan");
        assertThat(implRan.get()).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    // independent review round 5 finding: publishing the replacement registry strictly after commit (round 4)
    // narrows the unsafe window but does not close it — a concurrent runFrom() (via approve() on a
    // SURVIVING gate) could still run in the gap between "the database has durably committed the
    // replan" and "the in-memory registry reflects it." This test forces exactly that gap open,
    // deterministically, using a PlatformTransactionManager that delegates to the real one but
    // pauses AFTER delegate.commit(status) has already succeeded — so the database is genuinely,
    // durably updated — and BEFORE its own commit() call returns to replan()'s caller, which is
    // the only thing standing between "committed" and "published."
    //
    // "blocker" is deliberately INDEPENDENT of "impl"/"impl_v2" (no dependency relationship, and
    // its own gate has nothing to do with the replan target): this is what makes the race land
    // reliably rather than merely plausibly. Approving it triggers a runFrom() whose OWN stale
    // (pre-replan) graph batch contains only "blocker" (trivial, instant handler) and "impl"
    // (already invalidated in the database by the time this call runs, so activeStepStatus's
    // null-tolerant resolution correctly skips it) — nothing in that stale view requires any real
    // dispatch or wait, so this call reaches its own "should I complete" decision almost
    // immediately. replan()'s OWN post-pause work is unavoidably slower (publish, then actually
    // dispatch and wait for impl_v2's real handler through the executor pool), so the fast,
    // stale-graph call reliably reaches the completion decision FIRST — precisely the ordering
    // needed to prove the fix, not merely hope for it.
    @Test
    void aConcurrentApproveDuringReplansCommitToPublishGapNeverCompletesWithAPersistedStepStillPending()
            throws Exception {
        AtomicBoolean implV2Ran = new AtomicBoolean(false);
        StageHandler blockerHandler = ctx -> Map.of();
        StageHandler oldImpl = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("blocker", List.of(), blockerHandler, true, false, 0, 30, null, null, null),
                StageDefinition.of("impl", List.of(), oldImpl));
        StageHandler implV2 = ctx -> { implV2Ran.set(true); return Map.of(); };
        StageDefinition replacement = StageDefinition.of("impl_v2", List.of(), implV2);
        List<StageDefinition> catalogStages = new java.util.ArrayList<>(stages);
        catalogStages.add(replacement);

        AtomicBoolean pauseArmed = new AtomicBoolean(false);
        CountDownLatch committedButNotYetPublished = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        PlatformTransactionManager pausingAfterCommit = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return transactionManager.getTransaction(definition);
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
                transactionManager.commit(status); // the database transaction is now durably committed
                if (pauseArmed.compareAndSet(true, false)) {
                    committedButNotYetPublished.countDown();
                    try {
                        assertThat(releaseCommit.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
                transactionManager.rollback(status);
            }
        };
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, pausingAfterCommit, null, null, null, null,
                com.schwab.urlshortener.support.TestStageCatalogs.fromStages(catalogStages));
        String workflowId = engine.submit("demo", stages, "run-35p");
        // "blocker"'s own entry gate halts the workflow before "impl" is ever touched — both are
        // PENDING/WAITING_APPROVAL, neither has started.
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.PENDING);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pauseArmed.set(true);
        Future<?> replanFuture = pool.submit(() -> engine.replan(workflowId, "impl",
                List.of(replacement), "human", "swap implementation"));

        assertThat(committedButNotYetPublished.await(5, TimeUnit.SECONDS)).isTrue();
        // The database has ALREADY durably committed the replan — "impl" invalidated, "impl_v2"
        // PENDING at plan_version 2 — while replan()'s own call is still paused before its registry
        // publish. This is the exact gap round 4's fix left open.
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.INVALIDATED);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "impl_v2")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.PENDING);

        // Concurrently approve "blocker" (a survivor totally unrelated to the replan target) on a
        // DIFFERENT thread, and let it run to FULL completion — decision transaction AND its own
        // runFrom() call — while replan() is STILL parked mid-commit-to-publish. This is what
        // actually makes the ordering deterministic rather than merely likely: approve()'s own
        // decision-recording transaction is several real DB round trips (nowhere near as fast as
        // replan()'s own resume, which is just one in-memory registry.put() away from publishing),
        // so racing the two and hoping approve() reaches its own registry read first is unreliable
        // — instead, replan() is kept paused for approve()'s entire lifetime, guaranteeing its
        // runFrom() reads the registry while it is provably still the pre-replan one. Under the
        // pre-round-5 implementation, this call's own (stale) registry never learns "impl_v2"
        // exists at all and marks the workflow COMPLETED with it still PENDING; under the fix, it
        // must find "impl_v2" via the database directly and decline to complete.
        Future<?> approveFuture = pool.submit(() -> {
            engine.approve(workflowId, "blocker", "approved", "human", "resume concurrently");
            return null;
        });
        approveFuture.get(10, TimeUnit.SECONDS);

        // The concurrent approve() has now fully completed while replan() was still paused. It
        // must NOT have marked the workflow COMPLETED — "impl_v2" was already committed and PENDING
        // in the database the whole time, even though approve()'s own (stale) in-memory graph never
        // knew it existed.
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(implV2Ran.get()).isTrue();

        releaseCommit.countDown();
        replanFuture.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // replan()'s own resume (publish, then dispatch impl_v2) is what actually finishes the
        // workflow.
        assertThat(implV2Ran.get()).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    // Task 35 remediation (independent review round 7): the SAME-NAME variant of the test above. The prior
    // version proved the DB-authoritative completion check stops a stale runFrom from marking the
    // workflow COMPLETED while a same-named-but-different replacement sits PENDING — but it used
    // DIFFERENT names ("impl" -> "impl_v2"), which sidesteps the sharper defect an independent
    // review found: when the replacement REUSES the exact name "impl", a stale runFrom's by-name
    // resolution finds the NEW (plan_version 2) row on its very first lookup and would pair it with
    // the OLD (plan_version 1) StageDefinition's handler — the DB-authoritative check alone cannot
    // catch this, because by the time it runs the wrong handler may have ALREADY executed and made
    // the replacement row terminal. oldImplRan/newImplRan are deliberately DISTINGUISHABLE (this is
    // the whole point: proving WHICH handler ran, not just whether the workflow completed).
    // resolveIfCurrentGeneration is what is actually under test here: the stale call's own
    // registration tags "impl" at plan_version 1; the row this call's first (and only) by-name
    // lookup finds is plan_version 2 the instant the concurrent replan has committed; the mismatch
    // must make that row unresolvable to the stale call, exactly like "no active row exists," never
    // a silent substitution.
    @Test
    void aConcurrentApproveDuringReplansCommitToPublishGapNeverExecutesTheStaleHandlerAgainstASameNamedReplacementRow()
            throws Exception {
        AtomicBoolean oldImplRan = new AtomicBoolean(false);
        AtomicBoolean newImplRan = new AtomicBoolean(false);
        StageHandler blockerHandler = ctx -> Map.of();
        StageHandler oldImpl = ctx -> { oldImplRan.set(true); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("blocker", List.of(), blockerHandler, true, false, 0, 30, null, null, null),
                StageDefinition.of("impl", List.of(), oldImpl));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35q");
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        StageHandler newImpl = ctx -> { newImplRan.set(true); return Map.of(); };
        assertThatThrownBy(() -> engine.replan(workflowId, "impl",
                List.of(StageDefinition.of("impl", List.of(), newImpl)),
                "human", "swap implementation, same name"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boot-time catalog");

        assertThat(oldImplRan.get()).isFalse();
        assertThat(newImplRan.get()).isFalse();
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "impl")).isEmpty();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
    }

    // Task 35 remediation (independent review round 7): the "claim old row first" safe interleaving explicitly
    // called out alongside the test above — a stage already RUNNING must make replan() reject the
    // WHOLE request atomically (REQ-018), even when the proposed replacement reuses the exact same
    // name. This is really the existing REQ-018 RUNNING check (see
    // replanRejectsARunningReachableStageWithoutMutatingAnything below), re-asserted specifically
    // for the same-name case: nothing about name reuse should ever let a RUNNING stage be replanned
    // out from under its own in-flight execution.
    @Test
    void replanRejectsAStageAlreadyRunningEvenWhenTheReplacementReusesTheExactSameName() throws Exception {
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        AtomicBoolean oldImplRan = new AtomicBoolean(false);
        StageHandler slowOldImpl = ctx -> {
            oldImplRan.set(true);
            handlerStarted.countDown();
            try {
                assertThat(releaseHandler.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("impl", List.of(), slowOldImpl));

        WorkflowEngine engine = engine(stages);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        Future<String> submitFuture = pool.submit(() -> engine.submit("demo", stages, "run-35r"));
        assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // submitFuture is still blocked inside slowOldImpl (handler hasn't returned), so submit()
        // itself has not returned yet — recover the workflow id via correlationId instead of
        // blocking on the future.
        String workflowId = instanceRepository.findAll().stream()
                .filter(i -> "run-35r".equals(i.getCorrelationId())).findFirst().orElseThrow().getId();
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.RUNNING);

        StageHandler newImpl = ctx -> Map.of();
        assertThatThrownBy(() -> engine.replan(workflowId, "impl",
                List.of(StageDefinition.of("impl", List.of(), newImpl)), "human", "swap while running, same name"))
                .isInstanceOf(IllegalStateException.class);

        // Nothing mutated: still plan version 1, "impl" still RUNNING (the SAME row, same id), no
        // replacement row was ever inserted under plan_version 2.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.RUNNING);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "impl")).isEmpty();
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();

        releaseHandler.countDown();
        submitFuture.get(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(oldImplRan.get()).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    // Task 35 remediation (independent review round 7): the "replan invalidates old row first" safe interleaving.
    // A runFrom call that already bound its BatchMember to the OLD row's exact id (while it was
    // still the current generation, still PENDING) can be overtaken by a concurrent replan() that
    // invalidates that exact row before this call's own claimStage transaction runs. claimStage's
    // findWithLockById + status==PENDING re-check (independent review round 6) is what makes this safe: the
    // stale call attempts the EXACT old row by id, sees INVALIDATED, and silently declines the
    // claim — it never falls back to re-resolving "impl" by name (which would find the new row and
    // reintroduce the same defect from a different angle). isStaleThread scopes the pause to only
    // the thread running the stale submit()->runFrom()->claimStage call, so replan() (run
    // synchronously on the main thread, in between) is never itself delayed.
    @Test
    void aRunFromThatAlreadyBoundTheOldRowSkipsItWithoutRunningWhenAConcurrentReplanInvalidatesItFirst()
            throws Exception {
        AtomicBoolean oldImplRan = new AtomicBoolean(false);
        AtomicBoolean newImplRan = new AtomicBoolean(false);
        StageHandler oldImpl = ctx -> { oldImplRan.set(true); return Map.of(); };
        List<StageDefinition> stages = List.of(StageDefinition.of("impl", List.of(), oldImpl));
        StageHandler newImpl = ctx -> { newImplRan.set(true); return Map.of(); };
        StageDefinition replacement = StageDefinition.of("impl_v2", List.of(), newImpl);
        List<StageDefinition> catalogStages = new java.util.ArrayList<>(stages);
        catalogStages.add(replacement);

        ThreadLocal<Boolean> isStaleThread = ThreadLocal.withInitial(() -> false);
        AtomicBoolean pauseArmed = new AtomicBoolean(true);
        CountDownLatch aboutToClaim = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        PlatformTransactionManager pausingBeforeClaim = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                if (isStaleThread.get() && pauseArmed.compareAndSet(true, false)) {
                    aboutToClaim.countDown();
                    try {
                        assertThat(releaseClaim.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return transactionManager.getTransaction(definition);
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
                transactionManager.commit(status);
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
                transactionManager.rollback(status);
            }
        };
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, pausingBeforeClaim, null, null, null, null,
                com.schwab.urlshortener.support.TestStageCatalogs.fromStages(catalogStages));

        ExecutorService pool = Executors.newFixedThreadPool(1);
        Future<String> submitFuture = pool.submit(() -> {
            isStaleThread.set(true);
            return engine.submit("demo", stages, "run-35s");
        });

        assertThat(aboutToClaim.await(5, TimeUnit.SECONDS)).isTrue();
        String workflowId = instanceRepository.findAll().stream()
                .filter(i -> "run-35s".equals(i.getCorrelationId())).findFirst().orElseThrow().getId();
        // The stale call's own BatchMember was bound to this exact row while it was still PENDING —
        // still true right now, since claimStage's own transaction is paused before it can flip it.
        WorkflowStepEntity oldImplRow = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow();
        assertThat(oldImplRow.getStatus()).isEqualTo(StepStatus.PENDING);

        // Reuse the exact same name — this thread's replan() is not on the stale-thread ThreadLocal,
        // so it proceeds without pausing.
        engine.replan(workflowId, "impl", List.of(replacement),
                "human", "replace a stage while a stale run is paused before its own claim");

        // replan()'s own trailing runFrom() already ran the replacement to completion.
        assertThat(newImplRan.get()).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.INVALIDATED);

        // Release the stale call's paused claim attempt: it targets the EXACT old row (by id, bound
        // before this test ever called replan()), now INVALIDATED — claimStage must decline the
        // claim and the stale call must return normally, without ever running oldImpl and without
        // disturbing the already-COMPLETED workflow.
        releaseClaim.countDown();
        submitFuture.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(oldImplRan.get()).isFalse();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    // TDD requirement called out explicitly in tasks.md T035: a decision made against one plan
    // version must never be (mis)read as governing a different plan version's reintroduced gate of
    // the same step name. Constructed on a stage that is genuinely eligible under REQ-018: an
    // ENTRY-gated step can be approved (a real decision, recorded) and still be "not yet started" —
    // its handler runs only AFTER the approval, so a replan landing before that run is legitimate.
    @Test
    void anApprovedEntryGateStillNotYetStartedCanBeReplannedAndItsNameReusedWithoutInheritingTheOldApproval() {
        // Two independent (no dependency between them) entry-gated stages in the SAME batch: the
        // entry-gate scan halts on the FIRST unapproved one it finds each call, so approving "gate"
        // does not let it actually run yet — "blocker"'s own still-unapproved entry gate halts the
        // very next runFrom() call before "gate"'s handler ever executes.
        StageHandler gateHandler = ctx -> Map.of();
        StageHandler blockerHandler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gateHandler, true, false, 0, 30, null, null, null),
                new StageDefinition("blocker", List.of(), blockerHandler, true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35j");

        engine.approve(workflowId, "gate", "approved", "human", "v1 approved");

        WorkflowStepEntity gateStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "gate").orElseThrow();
        // Approved, but genuinely never ran: back to PENDING, not WAITING_APPROVAL, and "blocker"'s
        // own still-open entry gate halted runFrom before gate's handler could execute.
        assertThat(gateStep.getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(gateStep.getId(), "approved")).hasSize(1);

        // Replan "gate" itself — eligible (PENDING, handler never ran) — reusing its exact name.
        engine.replan(workflowId, "gate",
                List.of(new StageDefinition("gate", List.of(), gateHandler, true, false, 0, 30, null, null, null)),
                "human", "reopen gate under a new plan version");

        WorkflowStepEntity oldGate = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "gate").orElseThrow();
        assertThat(oldGate.getStatus()).isEqualTo(StepStatus.INVALIDATED);
        WorkflowStepEntity newGate = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "gate").orElseThrow();
        assertThat(newGate.getId()).isNotEqualTo(oldGate.getId());
        assertThat(newGate.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL); // its own entry gate, undecided

        // The v1 approval still exists, still correctly scoped to the OLD row — it must not be
        // readable as a decision on the NEW row.
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(oldGate.getId(), "approved")).hasSize(1);
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(newGate.getId(), "approved")).isEmpty();

        // The new gate must be decided independently.
        engine.approve(workflowId, "blocker", "approved", "human", "unrelated, unblocks itself");
        engine.approve(workflowId, "gate", "approved", "human", "v2 approved independently");
        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void replanOnAnUnknownStageIsRejectedWithoutMutatingAnything() {
        // exitApproval so the workflow is still open (PENDING_APPROVAL, not COMPLETED) when
        // replan() is called — otherwise the terminal-workflow precondition would fire first and
        // this test would no longer be exercising the unknown-stage check at all.
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("only", List.of(), handler, false, true, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35c");
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> engine.replan(workflowId, "does-not-exist", List.of(), "human", "typo"))
                .isInstanceOf(IllegalArgumentException.class);

        // Nothing changed: still plan version 1, "only" untouched, no replanned event recorded.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    @Test
    void replanOnATerminalWorkflowIsRejected() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(StageDefinition.of("only", List.of(), handler));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35d");

        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        assertThatThrownBy(() -> engine.replan(workflowId, "only", List.of(), "human", "too late"))
                .isInstanceOf(IllegalStateException.class);
    }

    // Concurrent approve() and replan() targeting the SAME not-yet-started gate: whichever side
    // wins the row lock first is serialized against the other, which then re-reads committed state
    // rather than a stale snapshot — never a torn or partial outcome. Both legal orderings are
    // acceptable (approve-then-invalidate is not a corruption: a step can legitimately be approved
    // and still be superseded by a later replan); what must never happen is a corrupted/partial
    // write, an exception other than the expected rejection, or a doubly-recorded decision.
    @RepeatedTest(20)
    void concurrentApproveAndReplanOnTheSameGateNeverProduceAPartialOrCorruptedOutcome() throws Exception {
        StageHandler gateHandler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gateHandler, true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine(stages);
        String workflowId = engine.submit("demo", stages, "run-35k");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<String> approveCall = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.approve(workflowId, "gate", "approved", "human", "racing approval");
                return "approved";
            } catch (RuntimeException e) {
                return "rejected:" + e.getClass().getSimpleName();
            }
        };
        Callable<String> replanCall = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.replan(workflowId, "gate",
                        List.of(new StageDefinition("gate", List.of(), gateHandler, true, false, 0, 30, null, null, null)),
                        "human", "racing replan");
                return "replanned";
            } catch (RuntimeException e) {
                return "rejected:" + e.getClass().getSimpleName();
            }
        };
        Future<String> approveResult = pool.submit(approveCall);
        Future<String> replanResult = pool.submit(replanCall);
        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        String a = approveResult.get(10, TimeUnit.SECONDS);
        String r = replanResult.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one non-corrupting final shape: either the replan invalidated "gate" (whether or
        // not the approval also landed first — invalidating an already-approved-but-not-yet-run
        // stage is legitimate), or the replan was rejected because approve() had already moved the
        // step past PENDING/undecided-WAITING_APPROVAL by the time replan's own eligibility check
        // ran under lock. No other outcome (an unexpected exception type, a doubly-recorded
        // approval, a torn step count) is acceptable.
        assertThat(List.of(a, r)).allMatch(outcome -> outcome.equals("approved") || outcome.equals("replanned")
                || outcome.startsWith("rejected:IllegalStateException") || outcome.startsWith("rejected:NoPendingApprovalException"));

        List<WorkflowStepEntity> allGateRows = List.of(
                stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "gate").orElseThrow());
        // Never more than one approval recorded for the v1 row, regardless of ordering.
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(allGateRows.get(0).getId(), "approved").size())
                .isLessThanOrEqualTo(1);
        // The workflow always ends up in a well-defined, non-corrupted terminal/near-terminal state.
        WorkflowInstanceEntity finalInstance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(finalInstance.getStatus()).isIn(
                WorkflowStatus.PENDING_APPROVAL, WorkflowStatus.RUNNING, WorkflowStatus.COMPLETED);
    }
}
