package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RegressionRiskStage {
    private RegressionRiskStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "an over-broad prefix check could reject legitimate public IPv6 addresses",
                "risk", "high",
                "mitigation", "test both fc00::/7 boundaries and a public IPv6 address");
        context.addArtifact("impact_regression_risk_finding", finding);
        return finding;
    }
}
