package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import com.schwab.urlshortener.reliability.PermanentStageException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChangeImpactReportStage {
    private static final List<String> DIMENSIONS = List.of(
            "components", "interfaces", "data_flows", "tests", "documentation",
            "regression_risk", "rollout_rollback");

    private ChangeImpactReportStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        List<String> missing = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) {
            Object finding = context.artifacts().get("impact_" + dimension + "_finding");
            if (!(finding instanceof Map<?, ?>)) {
                missing.add(dimension);
            } else {
                report.put(dimension, finding);
            }
        }

        if (!missing.isEmpty()) {
            context.recordDecision("agent", "change_impact_report_incomplete",
                    "missing impact dimensions: " + String.join(", ", missing));
            throw new PermanentStageException(
                    "change impact report is incomplete; missing: " + String.join(", ", missing));
        }

        report.put("complete", true);
        report.put("dimensionCount", DIMENSIONS.size());
        Map<String, Object> immutable = Collections.unmodifiableMap(new LinkedHashMap<>(report));
        context.addArtifact("changeImpactReport", immutable);
        context.addArtifact("brownfieldChange", true);
        context.addArtifact("scenarioType", "brownfield");
        context.recordDecision("agent", "change_impact_report_assembled",
                "joined all seven impact dimensions for mandatory human review");
        return immutable;
    }
}
