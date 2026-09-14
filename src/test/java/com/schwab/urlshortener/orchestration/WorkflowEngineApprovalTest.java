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
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null));

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
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null));

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
                new StageDefinition("design", List.of(), design, false, true, 0, 30, null),
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
}
