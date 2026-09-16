package com.schwab.urlshortener.orchestration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowContextTest {

    @Test
    void contextPreservesDecisionLineageInOrder() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.recordDecision("agent", "propose base62 codes", "deterministic, no collision");
        ctx.recordDecision("human", "approve", "matches ADR-003");

        assertThat(ctx.decisions().stream().map(d -> d.get("decision")).toList())
                .containsExactly("propose base62 codes", "approve");
        assertThat(ctx.decisions().get(0).get("actor")).isEqualTo("agent");
    }

    @Test
    void contextStoresArtifactsByName() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.addArtifact("normalizedRequirement", Map.of("text", "..."));

        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) ctx.artifacts().get("normalizedRequirement");
        assertThat(stored.get("text")).isEqualTo("...");
    }

    // independent review round 3 hardening (carried forward from Section 21): a handler must go through
    // addArtifact to contribute a value, never by mutating the map artifacts() returns directly.
    @Test
    void artifactsReturnsAReadOnlyViewThatStillReflectsLiveUpdates() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.addArtifact("a", 1);

        Map<String, Object> view = ctx.artifacts();
        assertThatThrownBy(() -> view.put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.remove("a")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);

        // Still a live VIEW, not a stale copy — a legitimate addArtifact call after the view was
        // obtained is reflected through it, and resetArtifactsTo (the internal mechanism) keeps
        // working since it mutates the backing map directly, never through this accessor.
        ctx.addArtifact("c", 3);
        assertThat(view).containsEntry("c", 3);
        ctx.resetArtifactsTo(Map.of("only", "this"));
        assertThat(view).containsExactly(Map.entry("only", "this"));
    }

    // 6th independent-review finding (Section 18 follow-up review, straggler-write race):
    // WorkflowEngine.runStage's beginAttempt-at-attempt-start fix (WorkflowContext.beginAttempt
    // clearing writesByStage for the stage) closes the case where a FAILED attempt's write sits
    // untouched until a LATER attempt starts. It does not, by itself, close a genuinely concurrent
    // case: TimeoutExecutor gives up on an attempt (future.get() times out) and starts the next
    // attempt WITHOUT waiting for the abandoned attempt's own worker thread to actually stop
    // (shutdownNow() only interrupts it; a handler stuck in non-interruptible work keeps running in
    // the background). That straggler thread's own InheritableThreadLocal currentStage slot is
    // still set to the stage's name — nothing clears one specific thread's own copy — so it can
    // call addArtifact AFTER a newer attempt has already begun, landing its stale write back in the
    // very bucket the new attempt is using. This test reproduces that ordering deterministically
    // (via latches controlling exactly when the "straggler" write happens, not by racing real
    // timing) and proves the write is dropped entirely — from writtenBy(...) AND from the shared
    // artifacts() map, never merely unattributed.
    @Test
    void lateWriteFromAnAbandonedAttemptNeverBecomesTrustedEvidenceEvenAfterANewerAttemptHasBegun()
            throws InterruptedException {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.beginStageAttribution("implementation");
        ctx.beginAttempt("implementation"); // attempt 1 (generation 1) begins

        CountDownLatch releaseStaleWrite = new CountDownLatch(1);
        CountDownLatch staleWriteAttempted = new CountDownLatch(1);

        // Simulates TimeoutExecutor's dedicated per-attempt worker thread for attempt 1: created
        // while attempt 1's generation is active, so it inherits BOTH the stage marker and
        // attempt 1's generation via InheritableThreadLocal at construction time, exactly like
        // production's per-attempt thread does.
        Thread abandonedAttemptWorker = new Thread(() -> {
            try {
                releaseStaleWrite.await();
                // This is the straggler: interrupted by TimeoutExecutor's shutdownNow() when
                // attempt 1 timed out, but still executing this line, writing AFTER attempt 2 has
                // already begun on the main thread below.
                ctx.addArtifact("urlSchemeAllowlistEnforced", true);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                staleWriteAttempted.countDown();
            }
        });
        abandonedAttemptWorker.start();

        // Attempt 2 begins on the original thread (as RetryExecutor's own retry loop does),
        // strictly before the straggler's write is released.
        ctx.beginAttempt("implementation"); // generation bumps to 2; bucket cleared
        releaseStaleWrite.countDown();
        staleWriteAttempted.await();
        abandonedAttemptWorker.join();

        assertThat(ctx.writtenBy("implementation")).isEmpty();
        assertThat(ctx.artifacts()).doesNotContainKey("urlSchemeAllowlistEnforced");

        ctx.endStageAttribution();
    }

    // 7th independent-review finding (Section 18 follow-up review, round 2 — the independent review's direct review
    // of the 6th finding's fix), gap (b): a generation-equality check against a monotonic counter
    // that only ever increases never actually CLOSES — the FINAL attempt of a runStage call has no
    // later attempt to invalidate it, so its generation stays "current" forever unless something
    // explicitly seals it. WorkflowEngine.runStage now calls sealAttempt(stageName) the instant its
    // retry loop concludes (success or exhaustion), before writtenBy(stageName) is read anywhere.
    // This test proves a straggler released strictly AFTER sealAttempt — with no newer attempt ever
    // having begun — is still dropped, closing the case the original generation-only check missed.
    @Test
    void lateWriteAfterSealIsDroppedEvenWithNoNewerAttemptHavingEverBegun() throws InterruptedException {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.beginStageAttribution("implementation");
        ctx.beginAttempt("implementation"); // the ONLY attempt (generation 1)

        CountDownLatch releaseStaleWrite = new CountDownLatch(1);
        CountDownLatch staleWriteAttempted = new CountDownLatch(1);
        Thread strayWorker = new Thread(() -> {
            try {
                releaseStaleWrite.await();
                ctx.addArtifact("ssrfGuardEnforced", true);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                staleWriteAttempted.countDown();
            }
        });
        strayWorker.start();

        // WorkflowEngine.runStage's own finally block: the retry loop has concluded, so this
        // stage's attempt bookkeeping is sealed — no newer beginAttempt is ever coming.
        ctx.sealAttempt("implementation");
        releaseStaleWrite.countDown();
        staleWriteAttempted.await();
        strayWorker.join();

        assertThat(ctx.writtenBy("implementation")).isEmpty();
        assertThat(ctx.artifacts()).doesNotContainKey("ssrfGuardEnforced");

        ctx.endStageAttribution();
    }

    // 7th independent-review finding, gap (c): attribution isolation is not the same as attempt
    // isolation. A validated write used to land in the shared `artifacts` map immediately, so even
    // though policy evaluation and persistence correctly ignored it (both read writtenBy(stage),
    // never artifacts()), a LATER attempt's own handler code reading ctx.artifacts() directly could
    // still observe an earlier, since-superseded attempt's value. addArtifact no longer writes
    // stage-attributed values into the shared map at all — only WorkflowEngine's existing
    // mergeTrustworthyArtifacts/resetArtifactsTo mechanism does that, at the batch boundary, once a
    // stage's own status proves it completed trustworthily. This test proves attempt 2 cannot
    // observe attempt 1's write via ctx.artifacts() at any point, not even while attempt 2 itself is
    // still running.
    @Test
    void aNewerAttemptNeverObservesAnEarlierAttemptsWriteViaTheSharedArtifactsMap() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.beginStageAttribution("implementation");

        ctx.beginAttempt("implementation"); // attempt 1
        ctx.addArtifact("urlSchemeAllowlistEnforced", true);
        assertThat(ctx.writtenBy("implementation")).containsEntry("urlSchemeAllowlistEnforced", true);
        // Not visible in the shared map even for the attempt that legitimately wrote it — only
        // WorkflowEngine's batch-boundary merge publishes into artifacts(), never addArtifact itself.
        assertThat(ctx.artifacts()).doesNotContainKey("urlSchemeAllowlistEnforced");

        ctx.beginAttempt("implementation"); // attempt 2 — supersedes attempt 1
        assertThat(ctx.writtenBy("implementation")).isEmpty(); // attempt 1's write is gone
        assertThat(ctx.artifacts()).doesNotContainKey("urlSchemeAllowlistEnforced"); // still absent

        ctx.sealAttempt("implementation");
        ctx.endStageAttribution();
    }

    // 7th independent-review finding, gap (a): the ORIGINAL fix's "read generation, then write"
    // was two separate, unsynchronized steps — a thread could pass the check, be preempted, let a
    // newer attempt begin, then resume and write anyway. beginAttempt/sealAttempt/addArtifact now
    // all synchronize on the same per-stage lock, making the check-and-write atomic, so this
    // interleaving is no longer merely unlikely — it is structurally impossible. This is a stress
    // test, not a single deterministic reproduction (a true TOCTOU window no longer exists to land
    // a latch-released write inside): a single driver thread advances through many attempts
    // sequentially — exactly how RetryExecutor actually drives beginAttempt for one stage, one
    // thread, one attempt at a time, never two beginAttempt calls for the same stage running
    // concurrently with each other — while a fresh straggler thread per attempt races to write
    // concurrently with everything the driver does afterward, exactly like an abandoned
    // TimeoutExecutor worker would. Kept the invariant strictly checkable, deliberately did NOT
    // drive beginAttempt itself from multiple concurrent threads: nothing in the production code
    // ever calls beginAttempt for the same stage from more than one thread at a time, so racing it
    // that way would test a scenario that cannot occur and — as an earlier version of this test
    // demonstrated by flaking on its own unsynchronized bookkeeping variable — has no well-defined
    // expected outcome to assert against.
    @RepeatedTest(20)
    void concurrentBeginAttemptAndAddArtifactNeverProduceATornOrStaleView() throws InterruptedException {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.beginStageAttribution("implementation");
        int attempts = 50;
        List<Thread> stragglers = new CopyOnWriteArrayList<>();
        for (int generation = 1; generation <= attempts; generation++) {
            ctx.beginAttempt("implementation"); // sequential — the real RetryExecutor loop's shape
            int myGeneration = generation;
            // Simulates an abandoned attempt's straggler worker thread: inherits this generation's
            // marker (a real straggler would via InheritableThreadLocal; captured explicitly here
            // for the same effect) and races to write it at some uncontrolled later point —
            // possibly during a LATER generation's own window, possibly after sealAttempt.
            Thread straggler = new Thread(() -> ctx.addArtifact("urlSchemeAllowlistEnforced", myGeneration));
            straggler.start();
            stragglers.add(straggler);
            // The attempt's OWN legitimate write, made inline while it is unambiguously still the
            // open generation (nothing else on this thread can have advanced `open` in between its
            // own beginAttempt call just above and this call) — deterministically guaranteed to
            // succeed, exactly like a handler's own addArtifact call during its own live attempt.
            ctx.addArtifact("urlSchemeAllowlistEnforced", myGeneration);
        }
        ctx.sealAttempt("implementation");
        for (Thread t : stragglers) {
            t.join();
        }

        // The final attempt's own inline write is guaranteed present (nothing ever supersedes it
        // before sealAttempt runs) and is the ONLY value that can legally survive — no earlier
        // generation's straggler, however it interleaved, could ever still be the one recorded, and
        // no torn/mixed state (more than the one key/value pair) can exist either.
        Map<String, Object> finalWrites = ctx.writtenBy("implementation");
        assertThat(finalWrites).hasSize(1);
        assertThat(finalWrites.get("urlSchemeAllowlistEnforced")).isEqualTo(attempts);

        ctx.endStageAttribution();
    }

    // the independent review's Section 18 follow-up round 2 explicitly asked this be re-proven at the WorkflowContext
    // level (not only via the higher-level WorkflowEngineReliabilityIntegrationTest): the per-stage
    // lock added to close gaps (a)/(b) above must not turn into a GLOBAL lock that serializes
    // unrelated stages, and beginAttempt/addArtifact for one stage must never delete or overwrite a
    // completely different stage's own writesByStage entry, even when both are hammered
    // concurrently and legitimately share an artifact key name.
    @Test
    void concurrentSiblingStagesWritingTheSameKeyNameRemainFullyIsolated() throws InterruptedException {
        WorkflowContext ctx = new WorkflowContext("run-1");
        List<String> stageNames = List.of("impact_components", "impact_interfaces", "impact_tests");
        Map<String, Object> expected = new ConcurrentHashMap<>();
        List<Thread> workers = new CopyOnWriteArrayList<>();
        CountDownLatch ready = new CountDownLatch(stageNames.size());
        CountDownLatch go = new CountDownLatch(1);

        for (String stage : stageNames) {
            Thread t = new Thread(() -> {
                WorkflowContext threadCtx = ctx; // same shared context, distinct stage attribution
                threadCtx.beginStageAttribution(stage);
                threadCtx.beginAttempt(stage);
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Object value = "value-from-" + stage;
                threadCtx.addArtifact("sharedKeyName", value);
                expected.put(stage, value);
                threadCtx.sealAttempt(stage);
                threadCtx.endStageAttribution();
            });
            workers.add(t);
            t.start();
        }
        ready.await();
        go.countDown();
        for (Thread t : workers) {
            t.join();
        }

        for (String stage : stageNames) {
            assertThat(ctx.writtenBy(stage)).containsEntry("sharedKeyName", expected.get(stage));
        }
    }
}
