package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedDocumentationStage {
    private ImpactedDocumentationStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "security posture and brownfield scenario evidence must include IPv6 ULA blocking",
                "risk", "low");
        context.addArtifact("impact_documentation_finding", finding);
        return finding;
    }
}
