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
    //
    // 7th independent-review finding (Section 18 follow-up review, round 2 — Codex's own direct
    // review of the 6th finding's fix): the 6th finding's fix (a per-stage attemptGeneration counter
    // checked, non-atomically, at the top of addArtifact) had three real gaps, all now closed by the
    // fields/methods below:
    //   (a) TOCTOU: reading `attemptGeneration.get(stage)` and then writing into `writesByStage` were
    //       two separate, unsynchronized steps. A straggler thread could read its generation as
    //       still-current, get preempted, let a newer beginAttempt() run to completion, then resume
    //       and perform its write — which the (already-passed) check never re-validates. Closed by
    //       making the read-check-and-write a single atomic block, synchronized per stage (openLocks
    //       below) — the same lock beginAttempt/sealAttempt also take, so no interleaving is
    //       possible between "is my generation still open" and "record my write" for a given stage.
    //   (b) No attempt-end invalidation: a generation stayed "current" forever once opened — nothing
    //       ever closed it, so the FINAL attempt of a runStage call (the one whose failure/success
    //       ends the retry loop with no further beginAttempt ever coming) could have a straggler
    //       write accepted indefinitely, including after runStage itself has returned and even after
    //       a later batch-level read of writtenBy(stage) has already happened. Closed by
    //       openGeneration (a per-stage "which generation, if any, is currently open" map, distinct
    //       from the monotonic attemptGeneration counter) and sealAttempt(stageName), which
    //       WorkflowEngine.runStage calls exactly once, synchronously, the moment its retry loop as a
    //       whole concludes (success or final exhaustion) — after that call, addArtifact rejects
    //       every write for that stage, forever, regardless of generation number.
    //   (c) Attribution isolation was not attempt isolation: even a validated write still landed in
    //       the shared `artifacts` map immediately, so a LATER attempt's handler reading
    //       ctx.artifacts() directly (not via writtenBy) could observe an EARLIER, since-superseded
    //       attempt's own value — the write was correctly excluded from policy evaluation and
    //       persistence (both read writtenBy(stage), never artifacts()), but not from a handler's own
    //       eyes. Closed by no longer writing stage-attributed values into `artifacts` at all from
    //       addArtifact — they live ONLY in writesByStage (this stage's own current-attempt buffer)
    //       until WorkflowEngine's existing, already-correct mergeTrustworthyArtifacts/
    //       resetArtifactsTo mechanism (Section 17/18) folds a batch's trustworthy per-stage writes
    //       into `artifacts` once, at the batch boundary — the same mechanism, and the same timing,
    //       that already governs what gets persisted and propagated into the next batch. No stage in
    //       the current codebase relies on reading back its own uncommitted write via ctx.artifacts()
    //       within a single attempt (confirmed by grep — no handler anywhere does this); if a future
    //       stage genuinely needs read-your-own-write within one attempt, it should read
    //       writtenBy(thisStageName) directly, not artifacts().
    private final Map<String, Map<String, Object>> writesByStage = new java.util.concurrent.ConcurrentHashMap<>();
    // Monotonically increasing per-stage attempt counter: bumped once per beginAttempt call, never
    // decremented, never reused. Distinct from openGeneration below — this counter's job is only to
    // hand each attempt a unique, ever-increasing identity to stamp its thread with.
    private final Map<String, Integer> attemptGeneration = new java.util.concurrent.ConcurrentHashMap<>();
    // Per-stage "which generation, if any, is currently open for writes" — set to the new generation
    // by beginAttempt, and removed entirely by sealAttempt once WorkflowEngine.runStage's retry loop
    // concludes (whether the stage ultimately succeeded or the retries were exhausted). A write whose
    // stamped generation doesn't match this map's CURRENT value for the stage — including because the
    // stage has no entry here at all, i.e. it was sealed — is stale and dropped. This is what actually
    // closes gap (b) above: attemptGeneration alone never shrinks back to "nothing is open", so a
    // generation-equality check against it alone can never expire on its own once the LAST attempt
    // begins.
    private final Map<String, Integer> openGeneration = new java.util.concurrent.ConcurrentHashMap<>();
    // One monitor object per stage name, created on first use. beginAttempt, sealAttempt, and the
    // check-and-write inside addArtifact all synchronize on the SAME object for a given stage name —
    // this is what makes "is my generation still open" and "record my write" atomic together (gap
    // (a) above), while leaving different stages (which never share a lock object) fully concurrent
    // with each other, preserving the existing sibling-stage parallelism.
    private final Map<String, Object> openLocks = new java.util.concurrent.ConcurrentHashMap<>();
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

    // Deliberately NOT attempt-isolated, unlike addArtifact — decisions are an append-only audit
    // log of "what was attempted", not evidence a policy or a later attempt is asked to trust, so a
    // failed attempt's own decision entry (e.g. "requirement_ingested" before it went on to throw)
    // is legitimate history, not stale evidence. This is an explicit design choice, not an oversight
    // (Codex's Section 18 follow-up round 2 asked this be called out rather than left silent): if a
    // future caller ever needs to filter decisions by which attempt produced them, the timestamp
    // already recorded here is sufficient to reconstruct that after the fact without new plumbing.
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
        if (stage == null) {
            // Unattributed call (no beginStageAttribution in effect) — e.g. a caller seeding
            // artifacts before any stage runs, or a unit test exercising WorkflowContext directly.
            // Never routed through attempt isolation, same as always.
            artifacts.put(name, value);
            return;
        }
        Integer mine = currentAttemptGeneration.get();
        synchronized (lockFor(stage)) {
            Integer open = openGeneration.get(stage);
            if (mine == null || open == null || !mine.equals(open)) {
                // Stale (a newer attempt has since begun), sealed (this stage's retry loop has
                // already concluded), or never-attempted (mine == null): dropped entirely. Not
                // merged into writesByStage, and — unlike an earlier version of this fix — never
                // merged into the shared `artifacts` map either, so a later attempt's own handler
                // reading ctx.artifacts() directly can never observe it (7th finding, gap (c)).
                return;
            }
            writesByStage.computeIfAbsent(stage, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(name, value);
        }
        // Deliberately NOT written into the shared `artifacts` map here. A stage's own writes
        // reach `artifacts` only via WorkflowEngine's existing mergeTrustworthyArtifacts/
        // resetArtifactsTo mechanism at the batch boundary, once this stage's status proves its
        // handler actually completed trustworthily — the same mechanism, and the same timing, that
        // already governs persistence and forward-propagation into the next batch. No stage in this
        // codebase reads back its own uncommitted write via ctx.artifacts() within one attempt.
    }

    // WorkflowEngine.runStage calls this immediately before invoking a stage's handler (on that
    // handler's own dedicated worker thread) and clears it in a finally block once the handler
    // returns or throws — every addArtifact call made by that handler (including across its own
    // internal retry attempts, which stay on the same thread) is attributed to this stage name.
    public void beginStageAttribution(String stageName) { currentStage.set(stageName); }
    public void endStageAttribution() { currentStage.remove(); }

    // 6th independent-review finding (Section 18 follow-up review): beginStageAttribution spans a
    // stage's WHOLE runStage call, including every one of its own automatic retry attempts
    // (RetryExecutor loops on the SAME thread, never resetting attribution between attempts) — so
    // a key an EARLIER, FAILED attempt wrote survived in writesByStage even if a LATER, successful
    // attempt never rewrote it. WorkflowEngine.runStage's perAttemptIsolated wrapper calls this at
    // the START of every individual attempt — it runs INSIDE TimeoutExecutor's own dedicated
    // per-attempt worker thread (as the first thing that thread does), not before that thread is
    // created; a straggler from an EARLIER attempt already has its own separate thread and its own
    // separate InheritableThreadLocal copies by the time this runs. Scoped strictly to this stage's
    // own name in every map here, so a concurrent sibling writing the same artifact key under a
    // different stage name is completely unaffected regardless of timing.
    public void beginAttempt(String stageName) {
        synchronized (lockFor(stageName)) {
            int generation = attemptGeneration.merge(stageName, 1, Integer::sum);
            currentAttemptGeneration.set(generation);
            openGeneration.put(stageName, generation);
            writesByStage.remove(stageName);
        }
    }

    // 7th independent-review finding (Section 18 follow-up review, round 2): closes the FINAL
    // attempt of a runStage call — the one after which no further beginAttempt will ever be called
    // for this stage, so a generation-equality check against attemptGeneration ALONE would stay
    // satisfied forever, accepting a straggler's write no matter how long after runStage returns it
    // arrives. WorkflowEngine.runStage calls this exactly once, synchronously, the instant its
    // retry loop as a whole concludes (success or final exhaustion) — before writtenBy(stageName)
    // is read for policy evaluation, and therefore also before the batch-level
    // mergeTrustworthyArtifacts reads it again later. After this call, EVERY future addArtifact for
    // this stage is rejected, permanently, regardless of what generation it claims.
    public void sealAttempt(String stageName) {
        synchronized (lockFor(stageName)) {
            openGeneration.remove(stageName);
        }
    }

    private Object lockFor(String stageName) {
        return openLocks.computeIfAbsent(stageName, k -> new Object());
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

    // Read-only view (Codex round 3 hardening, carried forward from Section 21): a handler must go
    // through addArtifact — with its attempt-generation check and per-stage attribution — to
    // contribute a value; it must never be able to bypass that by mutating the map this accessor
    // returns directly. Collections.unmodifiableMap wraps the SAME backing map (not a copy), so
    // resetArtifactsTo/addArtifact — which mutate the `artifacts` field directly, never through this
    // accessor — keep working exactly as before, and every caller reading through artifacts() still
    // sees live updates. The disclosed nested-mutation limitation (a caller reaching into a VALUE
    // that is itself a mutable Map/List and mutating it in place still bypasses attribution) is
    // unchanged by this — it is a different, deeper problem (deep immutability of arbitrary artifact
    // values) that this does not attempt to solve.
    public Map<String, Object> artifacts() { return Collections.unmodifiableMap(artifacts); }
}
