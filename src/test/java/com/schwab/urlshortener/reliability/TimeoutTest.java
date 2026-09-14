package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutTest {

    @Test
    void raisesOnExceedingTimeout() {
        StageHandler slow = ctx -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return Map.of();
        };
        StageHandler wrapped = TimeoutExecutor.withTimeout(slow, 50);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(StageTimeoutException.class);
    }

    @Test
    void returnsNormallyWithinTimeout() {
        StageHandler fast = ctx -> Map.of("ok", true);
        StageHandler wrapped = TimeoutExecutor.withTimeout(fast, 1000);

        assertThat(wrapped.run(null)).isEqualTo(Map.of("ok", true));
    }

    @Test
    void handlerExceptionWithinTimeoutPropagatesUnwrapped() {
        // Review finding: Task 33 is expected to compose this with RetryExecutor.withBoundedRetry
        // — TransientStageException/PermanentStageException thrown by the handler must reach the
        // retry wrapper as themselves, not get mis-shaped by this class's ExecutionException
        // unwrap logic (or, worse, get misclassified as a StageTimeoutException).
        StageHandler broken = ctx -> { throw new TransientStageException("simulated failure"); };
        StageHandler wrapped = TimeoutExecutor.withTimeout(broken, 1000);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(TransientStageException.class);
    }
}
