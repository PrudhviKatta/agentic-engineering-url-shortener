package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryTest {

    @Test
    void retriesTransientErrorUpToBoundThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler flaky = ctx -> {
            if (attempts.incrementAndGet() < 3) throw new TransientStageException("simulated");
            return Map.of("ok", true);
        };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(flaky, 3, 0);

        Map<String, Object> result = wrapped.run(null);

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void exhaustsRetriesAndThrows() {
        StageHandler alwaysFlaky = ctx -> { throw new TransientStageException("simulated"); };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(alwaysFlaky, 2, 0);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(TransientStageException.class);
    }

    @Test
    void permanentErrorIsNeverRetried() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler broken = ctx -> {
            attempts.incrementAndGet();
            throw new PermanentStageException("simulated");
        };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(broken, 5, 0);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(PermanentStageException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void backoffIsInvokedWithExponentiallyGrowingDelaysDeterministically() {
        // Codex REQUEST AMENDMENTS item 5: proves the backoff policy is genuinely invoked with the
        // correct exponentially-growing values, WITHOUT any wall-clock/timing assertion (which
        // would be inherently flaky under load) — a fake sleep function records exactly what
        // RetryExecutor computed and requested, in place of an actual Thread.sleep.
        List<Long> recordedBackoffs = new ArrayList<>();
        List<Integer> recordedRetryAttempts = new ArrayList<>();
        StageHandler alwaysFlaky = ctx -> { throw new TransientStageException("simulated"); };

        StageHandler wrapped = RetryExecutor.withBoundedRetry(alwaysFlaky, 3, 100L,
                recordedRetryAttempts::add, recordedBackoffs::add);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(TransientStageException.class);

        // maxRetries=3, backoffMillis=100: three retries actually performed (attempts 0,1,2), the
        // fourth (final, exhausting) failure never retries, so onRetry/sleepFn are each called
        // exactly 3 times, with backoff = 100 * 2^attempt for attempt in {0,1,2}.
        assertThat(recordedRetryAttempts).containsExactly(0, 1, 2);
        assertThat(recordedBackoffs).containsExactly(100L, 200L, 400L);
    }

    @Test
    void onRetryCallbackIsNeverInvokedOnTheFinalExhaustingFailure() {
        // A regression guard for the same off-by-one class of bug WorkflowEngine's own TDD cycle
        // caught: onRetry (and therefore any persisted retry counter built on it) must reflect
        // retries actually PERFORMED, bounded by maxRetries — not the raw failed-attempt count.
        AtomicInteger onRetryCallCount = new AtomicInteger(0);
        StageHandler alwaysFlaky = ctx -> { throw new TransientStageException("simulated"); };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(alwaysFlaky, 2, 0, a -> onRetryCallCount.incrementAndGet());

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(TransientStageException.class);

        assertThat(onRetryCallCount.get()).isEqualTo(2); // not 3
    }
}
