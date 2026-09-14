package com.schwab.urlshortener.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class StageCatalog {
    private final Map<String, StageHandler> handlersByStageName;
    private final Map<String, Predicate<WorkflowContext>> activationConditionsByKey;
    private final Map<String, StageHandler> fallbacksByKey;

    public StageCatalog(Map<String, StageHandler> handlersByStageName,
                         Map<String, Predicate<WorkflowContext>> activationConditionsByKey,
                         Map<String, StageHandler> fallbacksByKey) {
        this.handlersByStageName = Map.copyOf(handlersByStageName);
        this.activationConditionsByKey = Map.copyOf(activationConditionsByKey);
        this.fallbacksByKey = Map.copyOf(fallbacksByKey);
    }

    public StageHandler handlerFor(String stageName) {
        StageHandler handler = handlersByStageName.get(stageName);
        if (handler == null) {
            throw new IllegalStateException("no handler registered for stage: " + stageName);
        }
        return handler;
    }

    public Optional<Predicate<WorkflowContext>> activationConditionFor(String key) {
        return Optional.ofNullable(key).map(activationConditionsByKey::get);
    }

    public Optional<StageHandler> fallbackFor(String key) {
        return Optional.ofNullable(key).map(fallbacksByKey::get);
    }
}
