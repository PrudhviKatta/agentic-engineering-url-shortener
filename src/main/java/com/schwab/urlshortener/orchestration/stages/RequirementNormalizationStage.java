package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Locale;
import java.util.Map;

public final class RequirementNormalizationStage {
    private RequirementNormalizationStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Object raw = context.artifacts().get("requirementText");
        String normalized = raw instanceof String text
                ? text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                : "";
        context.addArtifact("normalizedRequirement", normalized);
        return Map.of("normalizedRequirement", normalized);
    }
}
