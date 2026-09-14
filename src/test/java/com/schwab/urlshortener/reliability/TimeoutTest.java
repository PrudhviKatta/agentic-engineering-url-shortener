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
}
