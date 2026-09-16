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
class AmbiguousScenarioTest {
    @Autowired WorkflowInstanceJpaRepository instances;
    @Autowired WorkflowStepJpaRepository steps;
    @Autowired WorkflowEventJpaRepository events;
    @Autowired ApprovalJpaRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void clarificationReasonIsFoldedIntoDecompositionAndWorkflowResumes() {
        WorkflowEngine engine = engine();
        String id = submitAmbiguous(engine, "scn-c-clarify");
        assertWaitingForClarification(id);
        assertThat(active(id, "decomposition").getStartedAt()).isNull();

        String clarification = "Limit the change to REQ-007 and verify 25-character URLs";
        engine.approve(id, "human_clarification", "approved", "reviewer", clarification);

        assertThat(active(id, "decomposition").getResult()).containsEntry("clarifiedRequirement", clarification);
        finishExitGates(engine, id);
        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void replanOccursBeforeApprovalAndRunsTheVersionedReplacement() {
        WorkflowEngine engine = engine();
        String id = submitAmbiguous(engine, "scn-c-replan");
        WorkflowStepEntity original = active(id, "decomposition");

        engine.replan(id, "decomposition", AmbiguousWorkflowGraph.replannedStages(), "reviewer",
                "narrow the work after clarification to one versioned delivery scope");
        assertWaitingForClarification(id);

        String clarification = "Apply only to REQ-007 and require 25-character test evidence";
        engine.approve(id, "human_clarification", "approved", "reviewer", clarification);
        finishExitGates(engine, id);

        assertThat(steps.findById(original.getId()).orElseThrow().getStatus()).isEqualTo(StepStatus.INVALIDATED);
        WorkflowStepEntity replacement = active(id, "decomposition_v2");
        assertThat(replacement.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(replacement.getResult()).containsEntry("clarifiedRequirement", clarification)
                .containsEntry("scopeVersion", 2);
        List<WorkflowEventEntity> audit = events.findByWorkflowIdOrderById(id);
        long replanned = audit.stream().filter(e -> e.getAction().equals("replanned"))
                .findFirst().orElseThrow().getId();
        long approved = audit.stream().filter(e -> e.getAction().equals("approval_approved:human_clarification"))
                .findFirst().orElseThrow().getId();
        assertThat(replanned).isLessThan(approved);
        assertThat(instances.findById(id).orElseThrow().getPlanVersion()).isEqualTo(2);
        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    private WorkflowEngine engine() {
        return new WorkflowEngine(instances, steps, events, approvals, transactions,
                null, null, null, null, BrownfieldWorkflowGraph.catalog());
    }

    private String submitAmbiguous(WorkflowEngine engine, String correlationId) {
        return engine.submit("ambiguous", BaselineWorkflowGraph.stages(), correlationId,
                "Make the short-link workflow better");
    }

    private void assertWaitingForClarification(String id) {
        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(active(id, "human_clarification").getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
    }

    private void finishExitGates(WorkflowEngine engine, String id) {
        engine.approve(id, "design", "approved", "reviewer", "design reflects the clarified scope");
        engine.approve(id, "release_readiness", "approved", "reviewer", "all exit evidence is present");
    }

    private WorkflowStepEntity active(String workflowId, String name) {
        return steps.findByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED).orElseThrow();
    }
}
