// src/main/java/com/schwab/urlshortener/reliability/CompensationRegistry.java
package com.schwab.urlshortener.reliability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Per ADR-009 (rollback vs. compensation): rollback is free via JPA transaction abort for effects
// that never left the transaction boundary; this registry handles effects already externally
// observed (e.g. a redirect already served) that a transaction abort can't undo. run()
// deliberately does NOT catch an exception thrown by a registered compensation function -- it must
// propagate to the same safe-stop path an exhausted retry uses, per ADR-009's own risk mitigation
// ("no third, divergent failure path for a failed compensation").
public class CompensationRegistry {

    // Review finding: WorkflowEngine.runBatch executes stage handlers concurrently across a
    // thread pool, and this registry doesn't yet pin down whether registration always happens
    // before any concurrent read (Task 33 wires this in). ConcurrentHashMap costs nothing here and
    // removes the latent trap either way.
    private final Map<String, Consumer<Map<String, Object>>> registry = new ConcurrentHashMap<>();

    public void register(String stageName, Consumer<Map<String, Object>> compensateFn) {
        registry.put(stageName, compensateFn);
    }

    // Returns whether a compensation ran. false means "nothing registered for this stage" -- by
    // design, not a failure signal on its own; callers that need compensation to be mandatory for
    // a given stage must check this themselves.
    public boolean run(String stageName, Map<String, Object> stageResult) {
        Consumer<Map<String, Object>> fn = registry.get(stageName);
        if (fn == null) return false;
        fn.accept(stageResult);
        return true;
    }
}
