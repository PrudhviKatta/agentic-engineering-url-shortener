package com.schwab.urlshortener.scenarios;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GreenfieldScenarioTest {
    @Autowired WorkflowInstanceJpaRepository instances;
    @Autowired WorkflowStepJpaRepository steps;
    @Autowired WorkflowEventJpaRepository events;
    @Autowired ApprovalJpaRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void clearRequirementSkipsClarificationAndCompletesThroughBothExitGates() {
        WorkflowEngine engine = new WorkflowEngine(instances, steps, events, approvals, transactions,
                null, null, null, null, BaselineWorkflowGraph.catalog());
        String id = engine.submit("greenfield", BaselineWorkflowGraph.stages(), "scn-a",
                "REQ-023: accept 20 concurrent link creations with zero duplicate short codes");

        assertThat(active(id, "human_clarification").getStatus()).isEqualTo(StepStatus.SKIPPED);
        assertThat(active(id, "design").getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(active(id, "implementation").getStatus()).isEqualTo(StepStatus.PENDING);

        engine.approve(id, "design", "approved", "reviewer", "design maps the concurrency criterion");

        assertThat(active(id, "release_readiness").getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(active(id, "release_readiness").getResult()).containsEntry("ready", true);
        engine.approve(id, "release_readiness", "approved", "reviewer",
                "test, security, documentation, and audit evidence are present");

        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(active(id, "final_engineering_summary").getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(steps.findByWorkflowIdAndStatusNotOrderBySequence(id, StepStatus.INVALIDATED))
                .hasSize(12).extracting(WorkflowStepEntity::getName)
                .containsExactlyElementsOf(BaselineWorkflowGraph.stages().stream()
                        .map(StageDefinition::name).toList());
        assertThat(events.findByWorkflowIdOrderById(id)).anySatisfy(event -> {
            assertThat(event.getAction()).isEqualTo("stage_skipped:human_clarification");
            assertThat(event.getReason()).contains("ambiguity_gate");
        });
    }

    private WorkflowStepEntity active(String workflowId, String name) {
        return steps.findByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED).orElseThrow();
    }
}
