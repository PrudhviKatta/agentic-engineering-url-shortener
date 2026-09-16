package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import com.schwab.urlshortener.orchestration.stages.impact.ImpactedComponentsStage;
import com.schwab.urlshortener.orchestration.stages.impact.ImpactedDataFlowsStage;
import com.schwab.urlshortener.orchestration.stages.impact.ImpactedDocumentationStage;
import com.schwab.urlshortener.orchestration.stages.impact.ImpactedInterfacesStage;
import com.schwab.urlshortener.orchestration.stages.impact.ImpactedTestsStage;
import com.schwab.urlshortener.orchestration.stages.impact.RegressionRiskStage;
import com.schwab.urlshortener.orchestration.stages.impact.RolloutRollbackStage;
import com.schwab.urlshortener.reliability.PermanentStageException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeImpactStageContractTest {

    @Test
    void eachParallelBranchPublishesItsNamedFindingAndRisk() {
        WorkflowContext context = new WorkflowContext("brownfield-contract");

        assertFinding(context, "impact_components_finding", ImpactedComponentsStage.run(context));
        assertFinding(context, "impact_interfaces_finding", ImpactedInterfacesStage.run(context));
        assertFinding(context, "impact_data_flows_finding", ImpactedDataFlowsStage.run(context));
        assertFinding(context, "impact_tests_finding", ImpactedTestsStage.run(context));
        assertFinding(context, "impact_documentation_finding", ImpactedDocumentationStage.run(context));
        assertFinding(context, "impact_regression_risk_finding", RegressionRiskStage.run(context));
        assertFinding(context, "impact_rollout_rollback_finding", RolloutRollbackStage.run(context));
    }

    @Test
    void rolloutBranchPublishesTheNestedRollbackContractReadinessConsumes() {
        WorkflowContext context = new WorkflowContext("brownfield-contract");

        Map<String, Object> finding = RolloutRollbackStage.run(context);

        assertThat(finding.get("rollbackPlan")).isInstanceOf(String.class);
        assertThat(String.valueOf(finding.get("rollbackPlan"))).isNotBlank();
        assertThat(context.artifacts())
                .containsEntry("impact_rollout_rollback_finding", finding);
    }

    @Test
    void joinedReportContainsAllSevenDimensionsAndMarksBrownfieldContext() {
        WorkflowContext context = new WorkflowContext("brownfield-contract");
        runAllBranches(context);

        Map<String, Object> report = ChangeImpactReportStage.run(context);

        assertThat(report).containsEntry("complete", true).containsEntry("dimensionCount", 7);
        assertThat(report.keySet()).containsAll(List.of(
                "components", "interfaces", "data_flows", "tests", "documentation",
                "regression_risk", "rollout_rollback"));
        assertThat(report.get("rollout_rollback"))
                .isEqualTo(context.artifacts().get("impact_rollout_rollback_finding"));
        assertThat(context.artifacts())
                .containsEntry("changeImpactReport", report)
                .containsEntry("brownfieldChange", true)
                .containsEntry("scenarioType", "brownfield");
        assertThat(decisionNames(context)).containsExactly("change_impact_report_assembled");
    }

    @Test
    void joinedReportRejectsAnyIncompleteImpactSetWithoutPublishingAPartialReport() {
        WorkflowContext context = new WorkflowContext("brownfield-contract");
        ImpactedComponentsStage.run(context);

        assertThatThrownBy(() -> ChangeImpactReportStage.run(context))
                .isInstanceOf(PermanentStageException.class)
                .hasMessageContaining("interfaces")
                .hasMessageContaining("rollout_rollback");
        assertThat(context.artifacts())
                .doesNotContainKeys("changeImpactReport", "brownfieldChange", "scenarioType");
        assertThat(decisionNames(context)).containsExactly("change_impact_report_incomplete");
    }

    private static void runAllBranches(WorkflowContext context) {
        ImpactedComponentsStage.run(context);
        ImpactedInterfacesStage.run(context);
        ImpactedDataFlowsStage.run(context);
        ImpactedTestsStage.run(context);
        ImpactedDocumentationStage.run(context);
        RegressionRiskStage.run(context);
        RolloutRollbackStage.run(context);
    }

    private static void assertFinding(WorkflowContext context, String key, Map<String, Object> finding) {
        assertThat(finding).containsKeys("finding", "risk");
        assertThat(finding.get("risk")).isIn("low", "medium", "high");
        assertThat(context.artifacts()).containsEntry(key, finding);
    }

    private static List<String> decisionNames(WorkflowContext context) {
        return context.decisions().stream().map(entry -> String.valueOf(entry.get("decision"))).toList();
    }
}
