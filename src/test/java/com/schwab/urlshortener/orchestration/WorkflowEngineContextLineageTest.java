package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom/approve dispatch stage handlers onto its internal thread pool,
// which borrows a *different* connection from the same DataSource; without this override
// those worker threads cannot see the test thread's uncommitted inserts (e.g. the
// WorkflowStepEntity rows submit() just wrote), and lookups by findByWorkflowId... throw
// NoSuchElementException. Same root cause and same fix as Task 22's
// WorkflowEngineExecutionTest and Task 23's WorkflowEngineApprovalTest — not a
// WorkflowEngine bug.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineContextLineageTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager);
    }

    @Test
    void decisionLineageSurvivesApprovalResumeBoundary() {
        StageHandler gate = ctx -> {
            ctx.recordDecision("agent", "propose", "r1");
            return Map.of();
        };
        StageHandler after = ctx -> {
            // lineage from before the gate must survive
            assertThat(ctx.decisions().stream().anyMatch(d -> "propose".equals(d.get("decision")))).isTrue();
            ctx.recordDecision("agent", "finish", "r2");
            return Map.of();
        };
        // exitApproval (not entryApproval): "gate"'s handler must actually run and record
        // its decision BEFORE the halt, so a fresh, unpersisted ctx on resume would lose it —
        // that loss is exactly the bug this task's fix must prevent.
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gate, false, true, 0, 30, null, null, null),
                StageDefinition.of("after", List.of("gate"), after)
        );

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-5");
        engine.approve(workflowId, "gate", "approved", "human", "ok");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) instance.getContextJson().get("decisions");
        assertThat(decisions.get(decisions.size() - 1).get("decision")).isEqualTo("finish");
    }
}
