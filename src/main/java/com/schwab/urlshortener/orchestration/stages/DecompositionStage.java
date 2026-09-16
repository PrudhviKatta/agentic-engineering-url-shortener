package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DecompositionStage {
    private DecompositionStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decomposed", true);
        result.put("workUnits", List.of("design", "implementation", "verification", "documentation"));
        result.put("acceptanceCriteriaDefined", true);

        Object resolution = context.artifacts().get("human_clarificationResolution");
        if (resolution != null) {
            result.put("clarifiedRequirement", resolution);
            context.recordDecision("agent", "decomposition_used_clarification",
                    String.valueOf(resolution));
        }
        Map<String, Object> immutable = Map.copyOf(result);
        context.addArtifact("decomposition", immutable);
        return immutable;
    }
}
