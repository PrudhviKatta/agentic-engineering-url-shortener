// src/main/java/com/schwab/urlshortener/reliability/CompensationRegistry.java
package com.schwab.urlshortener.reliability;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CompensationRegistry {

    private final Map<String, Consumer<Map<String, Object>>> registry = new HashMap<>();

    public void register(String stageName, Consumer<Map<String, Object>> compensateFn) {
        registry.put(stageName, compensateFn);
    }

    public boolean run(String stageName, Map<String, Object> stageResult) {
        Consumer<Map<String, Object>> fn = registry.get(stageName);
        if (fn == null) return false;
        fn.accept(stageResult);
        return true;
    }
}
