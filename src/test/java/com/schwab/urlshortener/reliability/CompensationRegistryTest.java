// src/test/java/com/schwab/urlshortener/reliability/CompensationRegistryTest.java
package com.schwab.urlshortener.reliability;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
