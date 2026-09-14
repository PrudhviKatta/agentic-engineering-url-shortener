package com.schwab.urlshortener.orchestration;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageCatalogTest {

    @Test
    void looksUpHandlerConditionAndFallbackByStableKey() {
        StageHandler handler = ctx -> Map.of("ran", true);
        StageHandler fallback = ctx -> Map.of("degraded", true);
        StageCatalog catalog = new StageCatalog(
                Map.of("my_stage", handler),
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>) ctx -> true),
                Map.of("doc_fallback", fallback));

        assertThat(catalog.handlerFor("my_stage")).isSameAs(handler);
        assertThat(catalog.activationConditionFor("ambiguity_gate")).isPresent();
        assertThat(catalog.activationConditionFor("nonexistent_key")).isEmpty();
        assertThat(catalog.fallbackFor("doc_fallback")).contains(fallback);
        assertThatThrownBy(() -> catalog.handlerFor("unknown_stage"))
                .isInstanceOf(IllegalStateException.class);
    }
}
