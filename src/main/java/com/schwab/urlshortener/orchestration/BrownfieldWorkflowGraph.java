package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.stages.ChangeImpactReportStage;
import com.schwab.urlshortener.orchestration.stages.DocumentationFallbackStage;
import com.schwab.urlshortener.orchestration.stages.impact.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Baseline workflow extended with the seven-branch brownfield impact gate. */
public final class BrownfieldWorkflowGraph {
    public static final List<String> IMPACT_STAGE_NAMES = List.of(
            "impacted_components", "impacted_interfaces", "impacted_data_flows", "impacted_tests",
            "impacted_documentation", "regression_risk", "rollout_rollback");

    private static final Map<String, StageHandler> IMPACT_HANDLERS = Map.of(
            "impacted_components", ImpactedComponentsStage::run,
            "impacted_interfaces", ImpactedInterfacesStage::run,
            "impacted_data_flows", ImpactedDataFlowsStage::run,
            "impacted_tests", ImpactedTestsStage::run,
            "impacted_documentation", ImpactedDocumentationStage::run,
            "regression_risk", RegressionRiskStage::run,
            "rollout_rollback", RolloutRollbackStage::run);
    private static final StageHandler REPORT = ChangeImpactReportStage::run;
    private static final List<StageDefinition> STAGES = buildStages();

    private BrownfieldWorkflowGraph() { }

    public static List<StageDefinition> stages() {
        return STAGES;
    }

    public static StageCatalog catalog() {
        Map<String, StageHandler> handlers = new LinkedHashMap<>();
        BaselineWorkflowGraph.stages().forEach(stage -> handlers.put(stage.name(), stage.handler()));
        IMPACT_HANDLERS.forEach(handlers::put);
        handlers.put("change_impact_report", REPORT);
        handlers.put("decomposition_v2", AmbiguousWorkflowGraph.decompositionV2Handler());
        return new StageCatalog(handlers,
                Map.of("ambiguity_gate", context -> Boolean.TRUE.equals(
                        context.artifacts().get("ambiguityDetected"))),
                Map.of("documentation_minimum", DocumentationFallbackStage::run));
    }

    private static List<StageDefinition> buildStages() {
        Map<String, StageDefinition> baseline = new LinkedHashMap<>();
        BaselineWorkflowGraph.stages().forEach(stage -> baseline.put(stage.name(), stage));
        List<StageDefinition> stages = new ArrayList<>();
        for (String name : List.of("requirement_ingestion", "requirement_normalization",
                "ambiguity_detection", "human_clarification", "decomposition", "design")) {
            stages.add(baseline.get(name));
        }
        IMPACT_STAGE_NAMES.forEach(name -> stages.add(new StageDefinition(name, List.of("design"),
                IMPACT_HANDLERS.get(name), false, false, 0, 30, "impact_analysis", null, null)));
        stages.add(new StageDefinition("change_impact_report", IMPACT_STAGE_NAMES, REPORT,
                false, true, 0, 30, null, null, null));
        StageDefinition implementation = baseline.get("implementation");
        stages.add(new StageDefinition(implementation.name(), List.of("change_impact_report"),
                implementation.handler(), implementation.entryApproval(), implementation.exitApproval(),
                implementation.maxRetries(), implementation.timeoutSeconds(), implementation.parallelGroup(),
                implementation.activationConditionKey(), implementation.fallbackHandlerKey()));
        for (String name : List.of("documentation", "testing", "security", "release_readiness",
                "final_engineering_summary")) {
            stages.add(baseline.get(name));
        }
        return List.copyOf(stages);
    }
}
