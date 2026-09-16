package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedInterfacesStage {
    private ImpactedInterfacesStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "no public API shape changes; URL validation rejects one additional private range",
                "risk", "low");
        context.addArtifact("impact_interfaces_finding", finding);
        return finding;
    }
}
