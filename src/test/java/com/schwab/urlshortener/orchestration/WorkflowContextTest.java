package com.schwab.urlshortener.orchestration;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
