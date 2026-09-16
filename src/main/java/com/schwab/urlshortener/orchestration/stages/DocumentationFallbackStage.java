package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces a deterministic, explicitly degraded documentation artifact. */
public final class DocumentationFallbackStage {
    private DocumentationFallbackStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> minimum = new LinkedHashMap<>();
        minimum.put("degraded", true);
        minimum.put("includes", List.of("approved design summary", "validation commands", "known limitations"));
        minimum.put("omits", List.of("rich formatting", "extended examples", "generated diagrams"));
        minimum.put("source", "approved design and persisted workflow evidence");
        Map<String, Object> result = Map.copyOf(minimum);
        context.addArtifact("documentationComplete", true);
        context.addArtifact("documentationEvidence", result);
        context.recordDecision("agent", "documentation_fallback_used",
                "produced the deterministic minimum documentation set with explicit omissions");
        return result;
    }
}
