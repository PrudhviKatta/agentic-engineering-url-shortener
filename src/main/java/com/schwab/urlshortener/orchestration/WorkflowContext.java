package com.schwab.urlshortener.orchestration;

import java.time.Instant;
import java.util.*;

public class WorkflowContext {

    private final String correlationId;
    private final List<Map<String, Object>> decisions;
    private final Map<String, Object> artifacts;

    // Independent-review finding (2nd Section 17 follow-up review, D1 fix-of-a-fix): a batch's
    // handlers run concurrently, each on its own thread (WorkflowEngine.runBatch dedicates one
    // thread per claimed stage). An EARLIER version of the per-stage attribution fix tried to infer
    // "what did this stage write" by diffing `artifacts` before/after that stage's own invocation —
    // that diff is racy: if a CONCURRENT sibling writes a key in the window between this stage's
    // before-snapshot and after-read, the diff attributes the sibling's write to THIS stage too,
    // since it only compares the shared map's overall state, not who actually wrote what.
    // Attributing at the exact moment of the write instead — via a per-thread "which stage is
    // currently running on me" marker, since exactly one stage's handler ever runs on a given
    // worker thread at a time — is race-free by construction: no diffing, no window to race in.
    private final Map<String, Set<String>> writesByStage = new java.util.concurrent.ConcurrentHashMap<>();
    // InheritableThreadLocal, not ThreadLocal: TimeoutExecutor.withTimeout runs the ACTUAL handler
    // on its own dedicated per-attempt worker thread (a fresh single-thread executor per attempt),
    // not on the thread runStage itself calls beginStageAttribution from. A plain ThreadLocal set
    // on runStage's thread would be invisible on that child thread, silently attributing nothing.
    // InheritableThreadLocal copies the parent thread's value into a child thread's own slot at
    // the moment the child Thread object is constructed — and that construction happens
    // synchronously on the PARENT's call stack, inside pool.submit(), which runs after
    // beginStageAttribution has already set the value there. Confirmed necessary, not defensive
    // paranoia: an earlier plain-ThreadLocal version of this field silently attributed zero writes
    // to any stage and was caught by the existing suite regressing (four unrelated tests expecting
    // a policy-blocked stage's own evidence to survive its own resume all failed).
    private final InheritableThreadLocal<String> currentStage = new InheritableThreadLocal<>();

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
        String stage = currentStage.get();
        if (stage != null) {
            writesByStage.computeIfAbsent(stage, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(name);
        }
    }

    // WorkflowEngine.runStage calls this immediately before invoking a stage's handler (on that
    // handler's own dedicated worker thread) and clears it in a finally block once the handler
    // returns or throws — every addArtifact call made by that handler (including across its own
    // internal retry attempts, which stay on the same thread) is attributed to this stage name.
    public void beginStageAttribution(String stageName) { currentStage.set(stageName); }
    public void endStageAttribution() { currentStage.remove(); }

    public Set<String> writtenBy(String stageName) {
        return writesByStage.getOrDefault(stageName, Set.of());
    }

    public String correlationId() { return correlationId; }
    public List<Map<String, Object>> decisions() { return decisions; }
    public Map<String, Object> artifacts() { return artifacts; }
}
