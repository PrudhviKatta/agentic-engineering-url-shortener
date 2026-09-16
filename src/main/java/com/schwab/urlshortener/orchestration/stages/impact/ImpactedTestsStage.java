package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.List;
import java.util.Map;

public final class ImpactedTestsStage {
    private ImpactedTestsStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", List.of(
                        "UrlValidatorSsrfTest IPv6 ULA rejection",
                        "UrlValidatorSsrfTest public IPv6 non-regression",
                        "UrlValidatorTest existing validation behavior"),
                "risk", "medium");
        context.addArtifact("impact_tests_finding", finding);
        return finding;
    }
}
