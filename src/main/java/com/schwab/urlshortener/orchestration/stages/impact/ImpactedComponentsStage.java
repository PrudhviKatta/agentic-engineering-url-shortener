package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedComponentsStage {
    private ImpactedComponentsStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "UrlValidator address classification is the only production component changed",
                "risk", "medium");
        context.addArtifact("impact_components_finding", finding);
        return finding;
    }
}
