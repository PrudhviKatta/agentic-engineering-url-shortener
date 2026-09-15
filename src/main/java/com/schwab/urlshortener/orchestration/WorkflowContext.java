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
    // 3rd independent-review finding (Section 17 follow-up review, D-NEW-1): the FIRST version of
    // this field only recorded WHICH keys a stage wrote (Map<String, Set<String>>), and the caller
    // (runFrom's safe-stop merge) re-read the VALUE for each surviving key from the live shared
    // `artifacts` map at merge time — which, by then, could have been overwritten by a DIFFERENT,
    // untrustworthy sibling writing the SAME key concurrently. The key-level filter correctly
    // proved "a trusted stage touched this key," then threw that proof away by reading whatever
    // value happened to be sitting in the shared map, trusted or not — reopening the exact
    // fail-open D1 was raised for. Recording the VALUE at the moment of the write (not just the
    // key) closes it: the caller now merges the values THIS map itself captured, never re-reading
    // the shared `artifacts` map for a value it wants to trust.
    private final Map<String, Map<String, Object>> writesByStage = new java.util.concurrent.ConcurrentHashMap<>();
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
            writesByStage.computeIfAbsent(stage, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(name, value);
        }
    }

    // WorkflowEngine.runStage calls this immediately before invoking a stage's handler (on that
    // handler's own dedicated worker thread) and clears it in a finally block once the handler
    // returns or throws — every addArtifact call made by that handler (including across its own
    // internal retry attempts, which stay on the same thread) is attributed to this stage name.
    public void beginStageAttribution(String stageName) { currentStage.set(stageName); }
    public void endStageAttribution() { currentStage.remove(); }

    // Returns exactly the (key, value) pairs THIS stage's own invocation wrote — captured at write
    // time, not re-read from the live `artifacts` map. A caller merging a trustworthy stage's
    // contribution into a persisted snapshot must use these values directly (e.g. via putAll), not
    // look the keys up in `artifacts()` afterward, or a concurrent untrustworthy sibling's later
    // overwrite of the same key would leak through the lookup.
    public Map<String, Object> writtenBy(String stageName) {
        return writesByStage.getOrDefault(stageName, Map.of());
    }

    // 4th independent-review finding (Section 17 follow-up review, Finding A): WorkflowEngine.
    // runFrom's next-batch baseline (batchStartArtifacts) is a snapshot of THIS live map, not of
    // whatever gets persisted to contextJson — so filtering only what's PERSISTED (excluding an
    // untrustworthy stage's writes) was not enough on its own: within ONE runFrom call spanning
    // multiple batches, the live map still carried the untrustworthy stage's writes forward into
    // the very next batch's baseline, even though the persisted DB row was correctly filtered.
    // runFrom calls this right after computing a batch's filtered/trustworthy view, resetting the
    // live map to match it — so both the persisted state AND every later batch's own baseline in
    // this same run agree on what's trustworthy, not just the DB row a separate later resume
    // would eventually read.
    public void resetArtifactsTo(Map<String, Object> values) {
        artifacts.clear();
        artifacts.putAll(values);
    }

    public String correlationId() { return correlationId; }
    public List<Map<String, Object>> decisions() { return decisions; }
    public Map<String, Object> artifacts() { return artifacts; }
}
