package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class HumanClarificationStage {
    private HumanClarificationStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Object resolution = context.artifacts().get("human_clarificationResolution");
        return resolution == null
                ? Map.of("clarificationRecorded", false)
                : Map.of("clarificationRecorded", true, "resolution", String.valueOf(resolution));
    }
}
