package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
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
}
