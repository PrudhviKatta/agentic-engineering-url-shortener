package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;
import java.util.regex.Pattern;

public final class AmbiguityDetectionStage {
    private static final Pattern REQUIREMENT_ID = Pattern.compile("\\breq-\\d+\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private AmbiguityDetectionStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Object raw = context.artifacts().get("normalizedRequirement");
        String normalized = raw instanceof String value ? value : "";
        boolean citesRequirement = REQUIREMENT_ID.matcher(normalized).find();
        String withoutRequirementIds = REQUIREMENT_ID.matcher(normalized).replaceAll("");
        boolean measurable = DIGIT.matcher(withoutRequirementIds).find();
        boolean ambiguous = !(citesRequirement && measurable);

        context.addArtifact("ambiguityDetected", ambiguous);
        String rationale = ambiguous
                ? "requirement lacks a cited requirement ID and/or a measurable test criterion"
                : "requirement cites a requirement ID and a measurable test criterion";
        context.recordDecision("agent",
                ambiguous ? "clarification_required" : "no_clarification_required", rationale);
        return Map.of(
                "citesRequirement", citesRequirement,
                "measurableCriterion", measurable,
                "ambiguityDetected", ambiguous,
                "rationale", rationale);
    }
}
