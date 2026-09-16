package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequirementIngestionStage {
    private RequirementIngestionStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Object stored = context.artifacts().get("requirement");
        String text = null;
        String submittedVia = null;
        if (stored instanceof Map<?, ?> requirement) {
            Object rawText = requirement.get("text");
            text = rawText instanceof String value ? value : null;
            Object rawSubmittedVia = requirement.get("submittedVia");
            submittedVia = rawSubmittedVia instanceof String value ? value : null;
        }

        boolean ingested = text != null && !text.isBlank();
        if (ingested) {
            // Extract the immutable String value; never retain or mutate the caller-owned nested map.
            context.addArtifact("requirementText", text);
        }
        context.recordDecision("agent", "requirement_ingested",
                ingested ? "captured the submitted requirement text verbatim"
                        : "no readable requirement text was submitted");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ingested", ingested);
        if (submittedVia != null) {
            result.put("submittedVia", submittedVia);
        }
        return Map.copyOf(result);
    }
}
