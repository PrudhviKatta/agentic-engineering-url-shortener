package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
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
}
