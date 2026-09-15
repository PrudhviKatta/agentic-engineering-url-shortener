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
    // 6th independent-review finding (Section 18 follow-up review, straggler-write race):
    // beginAttempt's writesByStage.remove(stageName) closes the case where a FAILED attempt's
    // evidence sits untouched until a LATER attempt starts. It does NOT close a genuinely
    // concurrent case: TimeoutExecutor gives up on an attempt (future.get() times out) and moves on
    // to the next attempt WITHOUT waiting for the abandoned attempt's worker thread to actually
    // stop — shutdownNow() only interrupts it, and a handler stuck in non-interruptible work keeps
    // running in the background (see TimeoutExecutor's own comment). That straggler thread still
    // has its InheritableThreadLocal currentStage slot set to this stage's name — nothing ever
    // clears a specific thread's own copy — so it can call addArtifact AFTER beginAttempt has
    // already reset the bucket for a NEW attempt, landing its stale write right back into the SAME
    // shared bucket the new attempt is using, with no way to tell the two apart by stage name
    // alone. attemptGeneration + currentAttemptGeneration below tag every write with the attempt
    // that produced it: beginAttempt bumps a per-stage counter and stamps that thread's own
    // generation; addArtifact drops (does not merge into writesByStage OR the shared `artifacts`
    // map at all) any write whose generation no longer matches the CURRENT live generation for that
    // stage at the moment the write happens. A straggler's generation is permanently stamped at its
    // own attempt's start and can never advance, so once a newer attempt has begun, every one of its
    // writes is provably stale and is discarded outright — not just excluded from attribution, but
    // never allowed to touch shared state a future batch's baseline could pick up either.
    private final Map<String, Integer> attemptGeneration = new java.util.concurrent.ConcurrentHashMap<>();
    private final InheritableThreadLocal<Integer> currentAttemptGeneration = new InheritableThreadLocal<>();
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
        String stage = currentStage.get();
        if (stage != null) {
            // A write attributed to a stage must additionally prove it came from the CURRENTLY
            // active attempt for that stage, not merely SOME attempt that once ran — see the
            // attemptGeneration field comment for the straggler-thread race this closes.
            Integer mine = currentAttemptGeneration.get();
            Integer active = attemptGeneration.get(stage);
            if (mine == null || !mine.equals(active)) {
                return; // stale/abandoned attempt: dropped entirely, never becomes trusted evidence
            }
            writesByStage.computeIfAbsent(stage, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(name, value);
        }
        artifacts.put(name, value);
    }

    // WorkflowEngine.runStage calls this immediately before invoking a stage's handler (on that
    // handler's own dedicated worker thread) and clears it in a finally block once the handler
    // returns or throws — every addArtifact call made by that handler (including across its own
    // internal retry attempts, which stay on the same thread) is attributed to this stage name.
    public void beginStageAttribution(String stageName) { currentStage.set(stageName); }
    public void endStageAttribution() { currentStage.remove(); }

    // 5th independent-review finding (Section 18 follow-up review): beginStageAttribution spans a
    // stage's WHOLE runStage call, including every one of its own automatic retry attempts
    // (RetryExecutor loops on the SAME thread, never resetting attribution between attempts) — so
    // a key an EARLIER, FAILED attempt wrote survived in writesByStage even if a LATER, successful
    // attempt never rewrote it, letting stale evidence from a dead attempt satisfy a policy check
    // the successful attempt never actually earned. WorkflowEngine.runStage calls this at the
    // START of every individual attempt (wrapping the handler itself, before TimeoutExecutor's own
    // per-attempt thread is even created) to discard exactly THIS stage's prior-attempt writes —
    // never another stage's, since writesByStage is keyed by stage name, so a concurrent sibling
    // writing the same artifact key is completely unaffected regardless of timing. Only the FINAL
    // attempt's own writes survive into writtenBy(stageName) by the time the handler returns.
    public void beginAttempt(String stageName) {
        int generation = attemptGeneration.merge(stageName, 1, Integer::sum);
        currentAttemptGeneration.set(generation);
        writesByStage.remove(stageName);
    }

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
