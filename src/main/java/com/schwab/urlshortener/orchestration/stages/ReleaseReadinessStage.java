package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseReadinessStage {
    private ReleaseReadinessStage() { }

    public static Map<String, Object> run(WorkflowContext context) {
        boolean testsPassed = Boolean.TRUE.equals(context.artifacts().get("testsPassed"));
        boolean securityPassed = Boolean.TRUE.equals(context.artifacts().get("securityChecksPassed"));
        boolean documentationComplete = Boolean.TRUE.equals(
                context.artifacts().get("documentationComplete"));
        boolean auditTrailPresent = context.correlationId() != null
                && !context.correlationId().isBlank();
        boolean mandatoryPolicyClear = isEmpty(context.artifacts().get("mandatoryPolicyFailures"));
        boolean exceptionsClear = isEmpty(context.artifacts().get("unapprovedPolicyExceptions"));
        boolean brownfield = Boolean.TRUE.equals(context.artifacts().get("brownfieldChange"))
                || "brownfield".equals(context.artifacts().get("scenarioType"));
        boolean rollbackPlanPresent = !brownfield || hasRollbackPlan(context.artifacts());

        List<String> reasons = new ArrayList<>();
        if (!testsPassed) reasons.add("tests have not produced passing evidence");
        if (!securityPassed) reasons.add("security checks have not produced passing evidence");
        if (!documentationComplete) reasons.add("documentation evidence is incomplete");
        if (!auditTrailPresent) reasons.add("workflow correlation evidence is missing");
        if (!mandatoryPolicyClear) reasons.add("mandatory policy failures remain");
        if (!exceptionsClear) reasons.add("unapproved policy exceptions remain");
        if (!rollbackPlanPresent) reasons.add("brownfield rollback plan is missing");

        List<String> degradedStages = stringList(context.artifacts().get("fallbacksUsed"));
        boolean ready = reasons.isEmpty();
        boolean degraded = ready && !degradedStages.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", ready);
        result.put("outcome", ready ? (degraded ? "READY_WITH_REDUCED_GUARANTEE" : "READY") : "NOT_READY");
        result.put("degraded", degraded);
        result.put("degradedStages", degradedStages);
        result.put("reasons", List.copyOf(reasons));
        result.put("evidence", Map.of(
                "testsPassed", testsPassed,
                "securityChecksPassed", securityPassed,
                "documentationComplete", documentationComplete,
                "auditTrailPresent", auditTrailPresent,
                "mandatoryPolicyClear", mandatoryPolicyClear,
                "exceptionsClear", exceptionsClear,
                "rollbackPlanPresent", rollbackPlanPresent));

        Map<String, Object> immutable = Map.copyOf(result);
        context.addArtifact("auditTrailPresent", auditTrailPresent);
        context.addArtifact("releaseReadiness", immutable);
        context.recordDecision("agent", degraded ? "release_readiness_degraded"
                        : ready ? "release_ready" : "release_not_ready",
                degraded ? "fallback use reduced the delivered guarantee"
                        : ready ? "all required evidence is present"
                        : String.join("; ", reasons));
        return immutable;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return false;
    }

    private static boolean hasRollbackPlan(Map<String, Object> artifacts) {
        Object direct = artifacts.get("rollbackPlan");
        if (direct instanceof String text && !text.isBlank()) return true;
        Object finding = artifacts.get("impact_rollout_rollback_finding");
        if (finding instanceof Map<?, ?> map) {
            Object nested = map.get("rollbackPlan");
            return nested instanceof String text && !text.isBlank();
        }
        return false;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).sorted().toList();
    }
}
