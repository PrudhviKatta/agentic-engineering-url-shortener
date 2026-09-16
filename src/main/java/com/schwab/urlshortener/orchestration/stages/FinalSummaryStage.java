package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinalSummaryStage {
    private FinalSummaryStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("correlationId", context.correlationId());
        Object readiness = context.artifacts().get("releaseReadiness");
        summary.put("releaseReadiness", readiness instanceof Map<?, ?> ? readiness : Map.of());
        summary.put("decisionCount", context.decisions().size());
        List<String> artifactNames = context.artifacts().keySet().stream().sorted().toList();
        summary.put("artifactNames", artifactNames);

        Map<String, Object> immutable = Map.copyOf(summary);
        context.addArtifact("finalEngineeringSummary", immutable);
        context.recordDecision("agent", "final_engineering_summary_recorded",
                "compiled deterministically from the workflow's persisted evidence set");
        return immutable;
    }
}
