package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class DocumentationStage {
    private DocumentationStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        boolean designAvailable = context.artifacts().get("design") instanceof Map<?, ?>;
        Map<String, Object> result = Map.of(
                "documentationComplete", designAvailable,
                "source", "approved design artifact");
        context.addArtifact("documentationComplete", designAvailable);
        context.addArtifact("documentationEvidence", result);
        return result;
    }
}
