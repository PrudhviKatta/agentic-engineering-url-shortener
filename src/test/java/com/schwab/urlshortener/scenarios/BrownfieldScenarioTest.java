package com.schwab.urlshortener.scenarios;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.orchestration.stages.DocumentationFallbackStage;
import com.schwab.urlshortener.support.TestStageCatalogs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BrownfieldScenarioTest {
    @Autowired WorkflowInstanceJpaRepository instances;
    @Autowired WorkflowStepJpaRepository steps;
    @Autowired WorkflowEventJpaRepository events;
    @Autowired ApprovalJpaRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void sevenImpactBranchesOverlapJoinAndRequireApprovalBeforeImplementation() {
        CountDownLatch allStarted = new CountDownLatch(BrownfieldWorkflowGraph.IMPACT_STAGE_NAMES.size());
        List<StageDefinition> graph = new ArrayList<>();
        for (StageDefinition stage : BrownfieldWorkflowGraph.stages()) {
            if (!BrownfieldWorkflowGraph.IMPACT_STAGE_NAMES.contains(stage.name())) {
                graph.add(stage);
                continue;
            }
            StageHandler synchronizedHandler = context -> {
                allStarted.countDown();
                try {
                    if (!allStarted.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("impact branches did not start together");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("impact synchronization interrupted", interrupted);
                }
                return stage.handler().run(context);
            };
            graph.add(new StageDefinition(stage.name(), stage.dependsOn(), synchronizedHandler,
                    stage.entryApproval(), stage.exitApproval(), stage.maxRetries(), stage.timeoutSeconds(),
                    stage.parallelGroup(), stage.activationConditionKey(), stage.fallbackHandlerKey()));
        }
        StageCatalog catalog = TestStageCatalogs.fromStages(graph,
                Map.of("ambiguity_gate", context -> Boolean.TRUE.equals(
                        context.artifacts().get("ambiguityDetected"))),
                Map.of("documentation_minimum", DocumentationFallbackStage::run));
        WorkflowEngine engine = new WorkflowEngine(instances, steps, events, approvals, transactions,
                null, null, null, null, catalog);
        String id = engine.submit("brownfield", graph, "scn-b",
                "REQ-021: reject 100 percent of IPv6 ULA fc00::/7 URL targets without rejecting public IPv6");

        engine.approve(id, "design", "approved", "reviewer",
                "impact analysis may proceed against the approved security design");

        List<WorkflowStepEntity> impact = BrownfieldWorkflowGraph.IMPACT_STAGE_NAMES.stream()
                .map(name -> active(id, name)).toList();
        assertThat(impact).allMatch(step -> step.getStatus() == StepStatus.SUCCEEDED);
        Instant latestStart = impact.stream().map(WorkflowStepEntity::getStartedAt).max(Instant::compareTo).orElseThrow();
        Instant earliestEnd = impact.stream().map(WorkflowStepEntity::getEndedAt).min(Instant::compareTo).orElseThrow();
        assertThat(latestStart).isBefore(earliestEnd.plusNanos(1));

        WorkflowStepEntity report = active(id, "change_impact_report");
        assertThat(report.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(report.getStartedAt()).isAfterOrEqualTo(
                impact.stream().map(WorkflowStepEntity::getEndedAt).max(Instant::compareTo).orElseThrow());
        assertThat(report.getResult()).containsEntry("dimensionCount", 7).containsEntry("complete", true);
        assertThat(active(id, "implementation").getStatus()).isEqualTo(StepStatus.PENDING);

        engine.approve(id, "change_impact_report", "rejected", "reviewer",
                "additional rollout evidence is required before implementation");

        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(active(id, "implementation").getStatus()).isEqualTo(StepStatus.PENDING);
    }

    private WorkflowStepEntity active(String workflowId, String name) {
        return steps.findByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED).orElseThrow();
    }
}
