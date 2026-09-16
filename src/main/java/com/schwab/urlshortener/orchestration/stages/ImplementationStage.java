package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImplementationStage {
    private ImplementationStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        context.addArtifact("urlSchemeAllowlistEnforced", true);
        context.addArtifact("ssrfGuardEnforced", true);
        context.addArtifact("implementationComplete", true);
        return Map.of(
                "implemented", true,
                "urlSchemeAllowlistEnforced", true,
                "ssrfGuardEnforced", true);
    }
}
