package com.schwab.urlshortener.orchestration;

import java.util.Map;

@FunctionalInterface
public interface StageHandler {
    Map<String, Object> run(WorkflowContext ctx);
}
