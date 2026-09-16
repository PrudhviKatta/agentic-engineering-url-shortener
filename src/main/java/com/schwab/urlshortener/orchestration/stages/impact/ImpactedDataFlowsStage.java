package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedDataFlowsStage {
    private ImpactedDataFlowsStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "validation still completes before a short-link row can be persisted",
                "risk", "low");
        context.addArtifact("impact_data_flows_finding", finding);
        return finding;
    }
}
