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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
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

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager);
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

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35a");

        // Halted at design's exit gate — impl (PENDING, blocked behind it) has not run.
        WorkflowInstanceEntity halted = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(halted.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(halted.getPlanVersion()).isEqualTo(1);
        Long designStepId = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "design").orElseThrow().getId();

        // Replan while design's own exit gate is STILL open (the governance-hole scenario) —
        // impl_v2 must not slip past it. "impl" is PENDING (never started) — eligible under REQ-018.
        AtomicBoolean ran = new AtomicBoolean(false);
        StageHandler newImpl = ctx -> { ran.set(true); return Map.of(); };
        engine.replan(workflowId, "impl", List.of(StageDefinition.of("impl_v2", List.of("design"), newImpl)),
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
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35e");
        WorkflowStepEntity reviewStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "review").orElseThrow();
        assertThat(reviewStep.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(reviewStep.getPreGateStatus()).isEqualTo(StepStatus.SUCCEEDED); // handler already ran

        assertThatThrownBy(() -> engine.replan(workflowId, "review",
                List.of(StageDefinition.of("review_v2", List.of(), ctx -> Map.of())), "human", "changed my mind"))
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
        WorkflowEngine engine = engine();

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
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35g");

        List<StageDefinition> cyclic = List.of(
                new StageDefinition("a", List.of("b"), handler, false, false, 0, 30, null, null, null),
                new StageDefinition("b", List.of("a"), handler, false, false, 0, 30, null, null, null));

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
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35h");

        List<StageDefinition> duplicated = List.of(
                StageDefinition.of("dup", List.of(), handler),
                StageDefinition.of("dup", List.of(), handler));

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
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35i");

        assertThatThrownBy(() -> engine.replan(workflowId, "gate", List.of(), " ", "a reason"))
                .isInstanceOf(InvalidApprovalDecisionException.class);
        assertThatThrownBy(() -> engine.replan(workflowId, "gate", List.of(), "human", ""))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        assertThat(instanceRepository.findById(workflowId).orElseThrow().getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
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
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35j");

        engine.approve(workflowId, "gate", "approved", "human", "v1 approved");

        WorkflowStepEntity gateStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "gate").orElseThrow();
        // Approved, but genuinely never ran: back to PENDING, not WAITING_APPROVAL, and "blocker"'s
        // own still-open entry gate halted runFrom before gate's handler could execute.
        assertThat(gateStep.getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(gateStep.getId(), "approved")).hasSize(1);

        // Replan "gate" itself — eligible (PENDING, handler never ran) — reusing its exact name.
        StageHandler newGateHandler = ctx -> Map.of();
        engine.replan(workflowId, "gate",
                List.of(new StageDefinition("gate", List.of(), newGateHandler, true, false, 0, 30, null, null, null)),
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
        WorkflowEngine engine = engine();
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
        WorkflowEngine engine = engine();
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
    @Test
    void concurrentApproveAndReplanOnTheSameGateNeverProduceAPartialOrCorruptedOutcome() throws Exception {
        StageHandler gateHandler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gateHandler, true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
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
