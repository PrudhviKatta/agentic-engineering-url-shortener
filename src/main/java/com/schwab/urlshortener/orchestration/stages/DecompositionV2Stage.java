package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** Replanned decomposition that narrows work around the recorded clarification. */
public final class DecompositionV2Stage {
    private DecompositionV2Stage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> result = new LinkedHashMap<>(DecompositionStage.run(context));
        result.put("scopeVersion", 2);
        result.put("scope", "clarified single-stage delivery with explicit validation evidence");
        Map<String, Object> immutable = Map.copyOf(result);
        context.addArtifact("decompositionV2", immutable);
        return immutable;
    }
}
