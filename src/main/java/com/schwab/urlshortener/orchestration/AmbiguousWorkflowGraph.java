package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.stages.DecompositionV2Stage;
import java.util.ArrayList;
import java.util.List;

/** Replacement subgraph used by the governed ambiguous-requirement replan. */
public final class AmbiguousWorkflowGraph {
    private static final StageHandler DECOMPOSITION_V2 = DecompositionV2Stage::run;

    private AmbiguousWorkflowGraph() { }

    public static StageHandler decompositionV2Handler() {
        return DECOMPOSITION_V2;
    }

    public static List<StageDefinition> replannedStages() {
        List<StageDefinition> replacements = new ArrayList<>();
        replacements.add(StageDefinition.of("decomposition_v2", List.of("human_clarification"),
                DECOMPOSITION_V2));
        for (StageDefinition stage : BaselineWorkflowGraph.stages()) {
            if (stage.name().equals("design")) {
                replacements.add(new StageDefinition(stage.name(), List.of("decomposition_v2"), stage.handler(),
                        stage.entryApproval(), stage.exitApproval(), stage.maxRetries(), stage.timeoutSeconds(),
                        stage.parallelGroup(), stage.activationConditionKey(), stage.fallbackHandlerKey()));
            } else if (List.of("implementation", "documentation", "testing", "security",
                    "release_readiness", "final_engineering_summary").contains(stage.name())) {
                replacements.add(stage);
            }
        }
        return List.copyOf(replacements);
    }
}
