package com.schwab.urlshortener.support;

import com.schwab.urlshortener.orchestration.StageCatalog;
import com.schwab.urlshortener.orchestration.StageDefinition;
import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class TestStageCatalogs {

    private TestStageCatalogs() { }

    public static StageCatalog fromStages(List<StageDefinition> stages) {
        Map<String, StageHandler> handlers = stages.stream().collect(Collectors.toUnmodifiableMap(
                StageDefinition::name, StageDefinition::handler));
        return new StageCatalog(handlers, Map.of(), Map.of());
    }

    public static StageCatalog fromStages(List<StageDefinition> stages,
            Map<String, Predicate<com.schwab.urlshortener.orchestration.WorkflowContext>> conditions,
            Map<String, StageHandler> fallbacks) {
        Map<String, StageHandler> handlers = stages.stream().collect(Collectors.toUnmodifiableMap(
                StageDefinition::name, StageDefinition::handler));
        return new StageCatalog(handlers, conditions, fallbacks);
    }
}
