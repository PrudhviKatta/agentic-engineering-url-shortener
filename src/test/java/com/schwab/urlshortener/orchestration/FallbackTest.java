package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.PermanentStageException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FallbackTest {
    @Autowired WorkflowInstanceJpaRepository instances;
    @Autowired WorkflowStepJpaRepository steps;
    @Autowired WorkflowEventJpaRepository events;
    @Autowired ApprovalJpaRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void successfulFallbackUnblocksDependentAndDisclosesReducedGuarantee() {
        StageHandler fails = ctx -> { throw new PermanentStageException("primary unavailable"); };
        StageHandler downstream = ctx -> Map.of("seen", ctx.artifacts().get("fallbacksUsed"));
        List<StageDefinition> graph = List.of(
                new StageDefinition("documentation", List.of(), fails, false, false, 2, 2,
                        null, null, "documentation_minimum"),
                StageDefinition.of("downstream", List.of("documentation"), downstream));
        StageCatalog catalog = new StageCatalog(
                Map.of("documentation", fails, "downstream", downstream), Map.of(),
                Map.of("documentation_minimum", ctx -> {
                    ctx.addArtifact("documentationComplete", true);
                    return Map.of("degraded", true, "omits", List.of("rich formatting"));
                }));

        String id = engine(catalog, new CompensationRegistry()).submit("fallback", graph, "fb-1");

        assertThat(active(id, "documentation").getStatus()).isEqualTo(StepStatus.SUCCEEDED_WITH_FALLBACK);
        assertThat(active(id, "downstream").getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(active(id, "downstream").getResult()).containsEntry("seen", List.of("documentation"));
        assertThat(events.findByWorkflowIdOrderById(id)).filteredOn(e ->
                        e.getAction().equals("fallback_started") || e.getAction().equals("fallback_succeeded"))
                .extracting(WorkflowEventEntity::getAction)
                .containsExactly("fallback_started", "fallback_succeeded");
        assertThat(events.findByWorkflowIdOrderById(id)).filteredOn(e -> e.getAction().startsWith("fallback_"))
                .allMatch(e -> e.getReason() != null && !e.getReason().isBlank());
    }

    @Test
    void failedFallbackFallsThroughToCompensationAndSafeStop() {
        StageHandler fails = ctx -> { throw new PermanentStageException("primary unavailable"); };
        List<StageDefinition> graph = List.of(new StageDefinition("documentation", List.of(), fails,
                false, false, 0, 2, null, null, "broken_fallback"));
        StageCatalog catalog = new StageCatalog(Map.of("documentation", fails), Map.of(),
                Map.of("broken_fallback", ctx -> { throw new PermanentStageException("fallback unavailable"); }));

        String id = engine(catalog, new CompensationRegistry()).submit("fallback-fails", graph, "fb-2");

        assertThat(active(id, "documentation").getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(instances.findById(id).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(events.findByWorkflowIdOrderById(id)).filteredOn(e -> e.getAction().equals("fallback_failed"))
                .singleElement().satisfies(e -> assertThat(e.getReason()).contains("fallback unavailable"));
    }

    @Test
    void parallelFallbackDisclosuresAreUnionedWithoutLostUpdates() {
        StageHandler failA = ctx -> { throw new PermanentStageException("a"); };
        StageHandler failB = ctx -> { throw new PermanentStageException("b"); };
        StageHandler join = ctx -> Map.of("used", ctx.artifacts().get("fallbacksUsed"));
        List<StageDefinition> graph = List.of(
                new StageDefinition("a", List.of(), failA, false, false, 0, 2, "parallel", null, "fa"),
                new StageDefinition("b", List.of(), failB, false, false, 0, 2, "parallel", null, "fb"),
                StageDefinition.of("join", List.of("a", "b"), join));
        StageCatalog catalog = new StageCatalog(Map.of("a", failA, "b", failB, "join", join), Map.of(),
                Map.of("fa", ctx -> Map.of("degraded", true), "fb", ctx -> Map.of("degraded", true)));

        String id = engine(catalog, new CompensationRegistry()).submit("parallel-fallback", graph, "fb-3");

        assertThat(active(id, "join").getResult()).containsEntry("used", List.of("a", "b"));
    }

    private WorkflowEngine engine(StageCatalog catalog, CompensationRegistry compensation) {
        return new WorkflowEngine(instances, steps, events, approvals, transactions,
                null, null, null, compensation, catalog);
    }

    private WorkflowStepEntity active(String workflowId, String name) {
        return steps.findByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED).orElseThrow();
    }
}
