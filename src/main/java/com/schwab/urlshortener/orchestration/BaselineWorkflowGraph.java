package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.stages.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The single production definition of the baseline twelve-stage workflow. */
public final class BaselineWorkflowGraph {
    private static final StageHandler REQUIREMENT_INGESTION = RequirementIngestionStage::run;
    private static final StageHandler REQUIREMENT_NORMALIZATION = RequirementNormalizationStage::run;
    private static final StageHandler AMBIGUITY_DETECTION = AmbiguityDetectionStage::run;
    private static final StageHandler HUMAN_CLARIFICATION = HumanClarificationStage::run;
    private static final StageHandler DECOMPOSITION = DecompositionStage::run;
    private static final StageHandler DESIGN = DesignStage::run;
    private static final StageHandler IMPLEMENTATION = ImplementationStage::run;
    private static final StageHandler TESTING = TestingStage::run;
    private static final StageHandler DOCUMENTATION = DocumentationStage::run;
    private static final StageHandler SECURITY = SecurityStage::run;
    private static final StageHandler RELEASE_READINESS = ReleaseReadinessStage::run;
    private static final StageHandler FINAL_SUMMARY = FinalSummaryStage::run;

    private static final List<StageDefinition> STAGES = List.of(
            stage("requirement_ingestion", List.of(), REQUIREMENT_INGESTION),
            stage("requirement_normalization", List.of("requirement_ingestion"), REQUIREMENT_NORMALIZATION),
            stage("ambiguity_detection", List.of("requirement_normalization"), AMBIGUITY_DETECTION),
            new StageDefinition("human_clarification", List.of("ambiguity_detection"), HUMAN_CLARIFICATION,
                    true, false, 0, 30, null, "ambiguity_gate", null),
            stage("decomposition", List.of("human_clarification"), DECOMPOSITION),
            new StageDefinition("design", List.of("decomposition"), DESIGN,
                    false, true, 0, 30, null, null, null),
            stage("implementation", List.of("design"), IMPLEMENTATION),
            new StageDefinition("documentation", List.of("design"), DOCUMENTATION,
                    false, false, 1, 30, "delivery", null, "documentation_minimum"),
            new StageDefinition("testing", List.of("implementation"), TESTING,
                    false, false, 1, 30, "verification", null, null),
            new StageDefinition("security", List.of("design", "implementation"), SECURITY,
                    false, false, 1, 30, "verification", null, null),
            new StageDefinition("release_readiness", List.of("testing", "security", "documentation"),
                    RELEASE_READINESS, false, true, 0, 30, null, null, null),
            stage("final_engineering_summary", List.of("release_readiness"), FINAL_SUMMARY));

    private BaselineWorkflowGraph() { }

    public static List<StageDefinition> stages() {
        return STAGES;
    }

    public static StageCatalog catalog() {
        Map<String, StageHandler> handlers = STAGES.stream().collect(
                Collectors.toUnmodifiableMap(StageDefinition::name, StageDefinition::handler));
        return new StageCatalog(handlers,
                Map.of("ambiguity_gate", context -> Boolean.TRUE.equals(
                        context.artifacts().get("ambiguityDetected"))),
                Map.of("documentation_minimum", DocumentationFallbackStage::run));
    }

    private static StageDefinition stage(String name, List<String> dependencies, StageHandler handler) {
        return StageDefinition.of(name, dependencies, handler);
    }
}
