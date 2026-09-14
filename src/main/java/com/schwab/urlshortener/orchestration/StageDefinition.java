package com.schwab.urlshortener.orchestration;

import java.util.List;

public record StageDefinition(
        String name,
        List<String> dependsOn,
        StageHandler handler,
        boolean entryApproval,
        boolean exitApproval,
        int maxRetries,
        int timeoutSeconds,
        String parallelGroup,
        String activationConditionKey,
        String fallbackHandlerKey
) {
    public static StageDefinition of(String name, List<String> dependsOn, StageHandler handler) {
        return new StageDefinition(name, dependsOn, handler, false, false, 0, 30, null, null, null);
    }
}
