package com.schwab.urlshortener.orchestration;

import java.time.Instant;
import java.util.*;

public class WorkflowContext {

    private final String correlationId;
    private final List<Map<String, Object>> decisions;
    private final Map<String, Object> artifacts;

    public WorkflowContext(String correlationId) {
        this(correlationId, new ArrayList<>(), new HashMap<>());
    }

    public WorkflowContext(String correlationId, List<Map<String, Object>> decisions, Map<String, Object> artifacts) {
        this.correlationId = correlationId;
        // CopyOnWriteArrayList/ConcurrentHashMap, not ArrayList/HashMap: from Task 40 onward,
        // multiple stages in the same parallel batch (e.g. SCN-B's seven impact branches, or a
        // stage recording a decision while a sibling's fallback records one) call
        // recordDecision()/addArtifact() concurrently from the batch's thread pool. A plain
        // HashMap under concurrent put() can corrupt its internal state, not just lose updates.
        this.decisions = new java.util.concurrent.CopyOnWriteArrayList<>(decisions);
        this.artifacts = new java.util.concurrent.ConcurrentHashMap<>(artifacts);
    }

    public void recordDecision(String actor, String decision, String rationale) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("actor", actor);
        entry.put("decision", decision);
        entry.put("rationale", rationale);
        entry.put("timestamp", Instant.now().toString());
        decisions.add(entry);
    }

    public void addArtifact(String name, Object value) {
        artifacts.put(name, value);
    }

    public String correlationId() { return correlationId; }
    public List<Map<String, Object>> decisions() { return decisions; }
    public Map<String, Object> artifacts() { return artifacts; }
}
