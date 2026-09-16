package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RolloutRollbackStage {
    private RolloutRollbackStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        Map<String, Object> finding = Map.of(
                "finding", "deploy as an isolated validation change with the existing regression suite as its gate",
                "risk", "low",
                "rollbackPlan", "revert the IPv6 ULA classification change; no schema or API migration is required");
        context.addArtifact("impact_rollout_rollback_finding", finding);
        return finding;
    }
}
