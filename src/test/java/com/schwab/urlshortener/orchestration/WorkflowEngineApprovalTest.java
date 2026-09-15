package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom/approve dispatch stage handlers onto its internal thread pool,
// which borrows a *different* connection from the same DataSource; without this override
// those worker threads cannot see the test thread's uncommitted inserts (e.g. the
// WorkflowStepEntity rows submit() just wrote), and lookups by findByWorkflowId... throw
// NoSuchElementException. Same root cause and same fix as Task 22's
// WorkflowEngineExecutionTest — not a WorkflowEngine bug.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineApprovalTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager);
    }

    @Test
    void workflowHaltsAtApprovalGateAndResumesOnApprove() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler risky = ctx -> { executed.add("risky"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null, null, null));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-3");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(executed).isEmpty(); // handler must NOT run before approval

        engine.approve(workflowId, "risky", "approved", "human", "looks safe");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(executed).containsExactly("risky");
    }

    @Test
    void rejectedApprovalFailsWorkflowWithoutRunningHandler() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler risky = ctx -> { executed.add("risky"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null, null, null));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-4");
        engine.approve(workflowId, "risky", "rejected", "human", "too risky");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(executed).isEmpty();
    }

    @Test
    void rejectedExitGateApprovalLeavesStepSucceededButBlocksDownstream() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler design = ctx -> { executed.add("design"); return Map.of(); };
        StageHandler after = ctx -> { executed.add("after"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("design", List.of(), design, false, true, 0, 30, null, null, null),
                StageDefinition.of("after", List.of("design"), after));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-4b");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(executed).containsExactly("design"); // exit gate: handler DID run before halting

        engine.approve(workflowId, "design", "rejected", "human", "wrong approach");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        WorkflowStepEntity designStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "design").orElseThrow();
        assertThat(designStep.getStatus()).isEqualTo(StepStatus.SUCCEEDED); // handler's own completion stands
        assertThat(executed).containsExactly("design"); // "after" must never run
    }

    // --- Round-6 regression tests (Codex REQUEST AMENDMENTS item 1) ---

    @Test
    void invalidDecisionValueIsRejected() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6a");

        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "maybe", "human", "unsure"))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL); // untouched by the rejected call
    }

    @Test
    void prematureApprovalOnAStepNotYetAtItsGateIsRejected() {
        // "after" depends on "gate" and never reaches WAITING_APPROVAL until "gate" is approved —
        // approving it early must be rejected, not silently accepted.
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null),
                StageDefinition.of("after", List.of("gate"), ctx -> Map.of()));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6b");

        assertThatThrownBy(() -> engine.approve(workflowId, "after", "approved", "human", "too early"))
                .isInstanceOf(NoPendingApprovalException.class);
    }

    @Test
    void duplicateApprovalOnAnAlreadyDecidedStepIsRejected() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6c");

        engine.approve(workflowId, "risky", "approved", "human", "ok");

        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", "human", "again"))
                .isInstanceOf(NoPendingApprovalException.class);
    }

    @Test
    void nonGateStepCanNeverBeApproved() {
        List<StageDefinition> stages = List.of(StageDefinition.of("plain", List.of(), ctx -> Map.of()));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6d");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "plain").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED); // ran unattended, no gate ever pending

        assertThatThrownBy(() -> engine.approve(workflowId, "plain", "approved", "human", "n/a"))
                .isInstanceOf(NoPendingApprovalException.class);
    }

    // --- Round-6 regression test (Codex REQUEST AMENDMENTS item 2) ---

    @Test
    void everyExitGatedSiblingInAParallelBatchRequiresItsOwnApproval() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler security = ctx -> { executed.add("security"); return Map.of(); };
        StageHandler documentation = ctx -> { executed.add("documentation"); return Map.of(); };
        StageHandler after = ctx -> { executed.add("after"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("security", List.of(), security, false, true, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of(), documentation, false, true, 0, 30, "verify", null, null),
                StageDefinition.of("after", List.of("security", "documentation"), after));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6e");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(executed).containsExactlyInAnyOrder("security", "documentation"); // both ran; neither gate skipped
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "security").orElseThrow().getStatus())
                .isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "documentation").orElseThrow().getStatus())
                .isEqualTo(StepStatus.WAITING_APPROVAL);

        engine.approve(workflowId, "security", "approved", "human", "ok");

        // "documentation" still hasn't been decided — the workflow must still be halted, and
        // "after" must not have run yet, even though "security" (the first sibling) is cleared.
        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(executed).doesNotContain("after");
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "documentation").orElseThrow().getStatus())
                .isEqualTo(StepStatus.WAITING_APPROVAL);

        engine.approve(workflowId, "documentation", "approved", "human", "ok");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        // security/documentation ran concurrently (same parallel batch) so their relative order
        // is not guaranteed; "after" must come last, once both siblings are individually approved.
        assertThat(executed).hasSize(3);
        assertThat(executed.get(2)).isEqualTo("after");
        assertThat(executed.subList(0, 2)).containsExactlyInAnyOrder("security", "documentation");
    }

    // --- Round-6b regression test (independent-review finding: rejection was not terminal) ---

    @Test
    void rejectingOneExitGatedSiblingFailsWorkflowAndBlocksApprovingTheOther() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler security = ctx -> { executed.add("security"); return Map.of(); };
        StageHandler documentation = ctx -> { executed.add("documentation"); return Map.of(); };
        StageHandler after = ctx -> { executed.add("after"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("security", List.of(), security, false, true, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of(), documentation, false, true, 0, 30, "verify", null, null),
                StageDefinition.of("after", List.of("security", "documentation"), after));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6f");

        engine.approve(workflowId, "security", "rejected", "human", "found a vulnerability");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);

        // Approving the OTHER sibling must not resurrect the failed workflow, must not re-open
        // "security"'s already-decided gate, and must not let "after" run.
        assertThatThrownBy(() -> engine.approve(workflowId, "documentation", "approved", "human", "ok"))
                .isInstanceOf(NoPendingApprovalException.class);

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED); // still terminal, not resurrected
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "security").orElseThrow().getStatus())
                .isEqualTo(StepStatus.SUCCEEDED); // NOT re-opened back to WAITING_APPROVAL
        assertThat(executed).doesNotContain("after");

        // A second decision on the already-rejected step itself must also still be refused.
        assertThatThrownBy(() -> engine.approve(workflowId, "security", "approved", "human", "changed my mind"))
                .isInstanceOf(NoPendingApprovalException.class);
    }

    @Test
    void approvingAnUnknownWorkflowIsRejected() {
        assertThatThrownBy(() -> engine().approve("does-not-exist", "risky", "approved", "human", "n/a"))
                .isInstanceOf(NoPendingApprovalException.class);
    }

    // --- Round-6c regression tests (item 2: ApprovalRequest validation) ---

    @Test
    void blankOrMissingActorIsRejected() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6h");

        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", null, "ok"))
                .isInstanceOf(InvalidApprovalDecisionException.class);
        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", "   ", "ok"))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        // Untouched by either rejected call — the gate is still exactly as it was. (@DataJpaTest's
        // datasource isn't reset between test methods within this class, so approvalRepository
        // still holds rows from earlier tests here — scope the "no row written" check to THIS step
        // instance rather than asserting the whole table is empty.)
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(approvalRepository.existsByStepInstanceId(step.getId())).isFalse();
    }

    @Test
    void blankOrMissingReasonIsRejected() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6i");

        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", "human", null))
                .isInstanceOf(InvalidApprovalDecisionException.class);
        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", "human", ""))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(approvalRepository.existsByStepInstanceId(step.getId())).isFalse();
    }

    // Codex round 4 finding: data-model.md Section 5 specifies workflow_events.reason (and
    // approvals.reason, decided_at time) as VARCHAR(1024) — a caller-supplied reason longer than
    // that must be rejected cleanly, not truncated or left to a raw DB constraint failure.
    @Test
    void overLengthReasonIsRejected() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6j");

        String tooLong = "x".repeat(1025);
        assertThatThrownBy(() -> engine.approve(workflowId, "risky", "approved", "human", tooLong))
                .isInstanceOf(InvalidApprovalDecisionException.class);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(approvalRepository.existsByStepInstanceId(step.getId())).isFalse();
    }

    // Codex round 5 finding: the boundary itself must actually work end-to-end, not just reject
    // one character over it — ApprovalEntity.reason previously had no explicit @Column length at
    // all (Hibernate default 255), so a 1024-character reason would have been silently truncated
    // or rejected by the database despite passing WorkflowEngine's own validation.
    @Test
    void exactly1024CharacterReasonPersistsSuccessfully() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6k");

        String exactlyMax = "x".repeat(1024);
        engine.approve(workflowId, "risky", "approved", "human", exactlyMax);

        // entryApproval: nothing else blocks it, so approve() dispatches it to completion in the
        // same call — its own trivial handler runs immediately.
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        ApprovalEntity approval = approvalRepository.findByStepInstanceIdAndDecision(step.getId(), "approved")
                .stream().findFirst().orElseThrow();
        assertThat(approval.getReason()).hasSize(1024).isEqualTo(exactlyMax);
    }

    // --- Round-6c regression test (item 1: atomic, one-shot decision recording) ---

    @Test
    void concurrentApproveAndRejectOnSameStepYieldsExactlyOneDecision() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler risky = ctx -> { executed.add("risky"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-6j");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<String> approveTask = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.approve(workflowId, "risky", "approved", "human-a", "looks fine");
                return "approved";
            } catch (NoPendingApprovalException e) {
                return "approve-lost";
            }
        };
        Callable<String> rejectTask = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.approve(workflowId, "risky", "rejected", "human-b", "too risky");
                return "rejected";
            } catch (NoPendingApprovalException e) {
                return "reject-lost";
            }
        };

        Future<String> approveFuture = pool.submit(approveTask);
        Future<String> rejectFuture = pool.submit(rejectTask);
        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown(); // release both threads at once — the DB row lock, not test timing, decides the winner
        String approveOutcome = approveFuture.get(10, TimeUnit.SECONDS);
        String rejectOutcome = rejectFuture.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one of the two calls actually recorded a decision; the other lost the race and
        // was rejected as "no pending gate" — never silently accepted, never silently dropped.
        List<String> outcomes = List.of(approveOutcome, rejectOutcome);
        boolean approveWon = outcomes.contains("approved");
        boolean rejectWon = outcomes.contains("rejected");
        assertThat(approveWon ^ rejectWon).isTrue(); // exactly one winner, never both, never neither

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "risky").orElseThrow();
        List<ApprovalEntity> approvalsForStep = approvalRepository.findAll().stream()
                .filter(a -> step.getId().equals(a.getStepInstanceId()))
                .toList();
        assertThat(approvalsForStep).hasSize(1); // exactly one ApprovalEntity row, never two

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        if (approveWon) {
            assertThat(approvalsForStep.get(0).getDecision()).isEqualTo("approved");
            assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
            assertThat(executed).containsExactly("risky");
        } else {
            assertThat(approvalsForStep.get(0).getDecision()).isEqualTo("rejected");
            assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
            assertThat(executed).isEmpty(); // entry gate: handler never ran
        }
    }

    // --- Codex REQUEST AMENDMENTS item 7: workflow-instance-level locking ---

    @Test
    void concurrentDecisionsOnDifferentSiblingGatesCannotResurrectARejectedWorkflow() throws Exception {
        // The previously-disclosed cross-sibling race: two DIFFERENT exit-gated siblings decided
        // truly simultaneously (reject one, approve the other) must never let the approved
        // sibling's resume carry a rejected workflow through to COMPLETED, regardless of which
        // decision's transaction happens to commit first.
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler security = ctx -> { executed.add("security"); return Map.of(); };
        StageHandler documentation = ctx -> { executed.add("documentation"); return Map.of(); };
        StageHandler after = ctx -> { executed.add("after"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("security", List.of(), security, false, true, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of(), documentation, false, true, 0, 30, "verify", null, null),
                StageDefinition.of("after", List.of("security", "documentation"), after));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-7a");
        WorkflowInstanceEntity beforeRace = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(beforeRace.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<String> rejectSecurity = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.approve(workflowId, "security", "rejected", "human-a", "found a vulnerability");
                return "rejected-security";
            } catch (NoPendingApprovalException e) {
                return "reject-lost";
            }
        };
        Callable<String> approveDocumentation = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                engine.approve(workflowId, "documentation", "approved", "human-b", "looks good");
                return "approved-documentation";
            } catch (NoPendingApprovalException e) {
                return "approve-lost";
            }
        };

        Future<String> f1 = pool.submit(rejectSecurity);
        Future<String> f2 = pool.submit(approveDocumentation);
        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Regardless of which decision's transaction committed first, the workflow must end up
        // FAILED (the rejection always wins), and "after" — which depends on BOTH siblings — must
        // never have run, since one of its dependencies was rejected.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(executed).doesNotContain("after");
    }

    // --- Independent-review finding (post-item-7 follow-up): concurrent APPROVE+APPROVE on
    // different sibling exit gates must not double-execute the dependent stage's handler ---

    @Test
    void concurrentApprovalsOnDifferentSiblingGatesRunTheDependentHandlerExactlyOnce() throws Exception {
        // Both siblings get APPROVED (not one rejected) truly concurrently — the interleaving the
        // rejection-focused test above cannot exercise, since a rejection short-circuits before
        // ever calling runFrom(). Here BOTH approve() calls reach runFrom() concurrently, and both
        // can legitimately see "after" as PENDING before either dispatches it.
        java.util.concurrent.atomic.AtomicInteger afterInvocations = new java.util.concurrent.atomic.AtomicInteger();
        StageHandler security = ctx -> Map.of();
        StageHandler documentation = ctx -> Map.of();
        StageHandler after = ctx -> { afterInvocations.incrementAndGet(); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("security", List.of(), security, false, true, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of(), documentation, false, true, 0, 30, "verify", null, null),
                StageDefinition.of("after", List.of("security", "documentation"), after));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-7b");
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Void> approveSecurity = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            engine.approve(workflowId, "security", "approved", "human-a", "ok");
            return null;
        };
        Callable<Void> approveDocumentation = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            engine.approve(workflowId, "documentation", "approved", "human-b", "ok");
            return null;
        };

        Future<Void> f1 = pool.submit(approveSecurity);
        Future<Void> f2 = pool.submit(approveDocumentation);
        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(afterInvocations.get()).isEqualTo(1);
    }

    // --- Scoped re-review finding: claimStage alone stops double-execution but not the losing
    // thread racing ahead into a batch DOWNSTREAM of the one it lost. A losing thread that finds
    // its own `pending` empty must not treat that as "nothing left to do here" and fall through
    // to the next batch while the winner's stage is still RUNNING. ---

    @Test
    void loserOfAClaimRaceDoesNotRunADependentBatchWhileTheWinnersSiblingIsStillRunning() throws Exception {
        CountDownLatch afterMayFinish = new CountDownLatch(1);
        CountDownLatch tailStarted = new CountDownLatch(1);
        AtomicBoolean tailObservedAfterStillRunning = new AtomicBoolean(false);
        StageHandler security = ctx -> Map.of();
        StageHandler documentation = ctx -> Map.of();
        StageHandler after = ctx -> {
            try {
                afterMayFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        };
        StageHandler tail = ctx -> {
            if (afterMayFinish.getCount() > 0) {
                tailObservedAfterStillRunning.set(true);
            }
            tailStarted.countDown();
            return Map.of();
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("security", List.of(), security, false, true, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of(), documentation, false, true, 0, 30, "verify", null, null),
                StageDefinition.of("after", List.of("security", "documentation"), after),
                StageDefinition.of("tail", List.of("after"), tail));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-7c");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Void> approveSecurity = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            engine.approve(workflowId, "security", "approved", "human-a", "ok");
            return null;
        };
        Callable<Void> approveDocumentation = () -> {
            bothReady.countDown();
            go.await(5, TimeUnit.SECONDS);
            engine.approve(workflowId, "documentation", "approved", "human-b", "ok");
            return null;
        };

        Future<Void> f1 = pool.submit(approveSecurity);
        Future<Void> f2 = pool.submit(approveDocumentation);
        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        // "after" is blocked on its latch. Give a buggy loser thread a real window to race ahead
        // into "tail" before we release it — this must NOT happen regardless of how long we wait.
        assertThat(tailStarted.await(500, TimeUnit.MILLISECONDS)).isFalse();

        afterMayFinish.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        assertThat(tailStarted.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(tailObservedAfterStillRunning.get()).isFalse();
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    // --- Codex REQUEST AMENDMENTS follow-up (Section 17 review): runBatch must keep waiting for
    // an interrupted future's SAME handler, not move on and leave it orphaned ---

    @Test
    void aBatchThreadInterruptedWhileWaitingStillJoinsTheHandlerBeforeReturning() throws Exception {
        CountDownLatch handlerMayFinish = new CountDownLatch(1);
        AtomicBoolean handlerFinished = new AtomicBoolean(false);
        StageHandler slow = ctx -> {
            try {
                handlerMayFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handlerFinished.set(true);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("slow", List.of(), slow));

        WorkflowEngine engine = engine();
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        AtomicReference<String> workflowIdRef = new AtomicReference<>();
        Thread submitter = new Thread(() -> {
            try {
                workflowIdRef.set(engine.submit("demo", stages, "run-7d"));
            } catch (Throwable t) {
                escaped.set(t);
            }
        });
        submitter.start();

        // Interrupt the submitting thread while it is very likely still blocked in
        // runBatch's future.get() — the handler itself is still held on handlerMayFinish, so the
        // interrupt cannot possibly land after the handler has already completed.
        Thread.sleep(100);
        submitter.interrupt();
        submitter.interrupt(); // twice, to exercise being interrupted more than once while waiting

        // Give the (buggy, pre-fix) code a real window to return early before the handler is
        // even allowed to finish — this must NOT happen.
        Thread.sleep(200);
        assertThat(handlerFinished.get()).isFalse();
        assertThat(submitter.isAlive()).isTrue(); // still blocked, correctly still waiting

        handlerMayFinish.countDown();
        submitter.join(10_000);

        assertThat(submitter.isAlive()).isFalse();
        assertThat(handlerFinished.get()).isTrue();
        assertThat(escaped.get()).isNull(); // no exception escaped submit() — the handler succeeded
        String workflowId = workflowIdRef.get();
        assertThat(workflowId).isNotNull();
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "slow")
                .orElseThrow().getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }
}
