// src/test/java/com/schwab/urlshortener/reliability/CompensationRegistryTest.java
package com.schwab.urlshortener.reliability;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompensationRegistryTest {

    @Test
    void registeredCompensationRunsWithStageResult() {
        CompensationRegistry registry = new CompensationRegistry();
        Map<String, Object> seen = new HashMap<>();
        registry.register("notify_customer", result -> seen.put("undoFor", result.get("externalRef")));

        boolean ran = registry.run("notify_customer", Map.of("externalRef", "msg-123"));

        assertThat(ran).isTrue();
        assertThat(seen.get("undoFor")).isEqualTo("msg-123");
    }

    @Test
    void unregisteredStageReturnsFalseWithoutError() {
        CompensationRegistry registry = new CompensationRegistry();
        assertThat(registry.run("unregistered_stage", Map.of())).isFalse();
    }

    @Test
    void aFailingCompensationPropagatesRatherThanBeingSwallowed() {
        // ADR-009: a failed compensation must fall through to the same safe-stop path an
        // exhausted retry uses -- no third, divergent failure path. This locks in that run()
        // never catches an exception thrown by the registered function, so a future edit can't
        // silently add a swallow-and-continue without breaking this test.
        CompensationRegistry registry = new CompensationRegistry();
        registry.register("notify_customer", result -> { throw new IllegalStateException("compensation itself failed"); });

        assertThatThrownBy(() -> registry.run("notify_customer", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("compensation itself failed");
    }
}
