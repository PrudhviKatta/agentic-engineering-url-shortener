package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class SecurityStage {
    private SecurityStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        boolean allowlist = Boolean.TRUE.equals(
                context.artifacts().get("urlSchemeAllowlistEnforced"));
        boolean ssrfGuard = Boolean.TRUE.equals(context.artifacts().get("ssrfGuardEnforced"));
        boolean passed = allowlist && ssrfGuard;
        Map<String, Object> result = Map.of(
                "urlSchemeAllowlistEnforced", allowlist,
                "ssrfGuardEnforced", ssrfGuard,
                "securityChecksPassed", passed);
        context.addArtifact("securityChecksPassed", passed);
        context.addArtifact("securityResults", result);
        return result;
    }
}
