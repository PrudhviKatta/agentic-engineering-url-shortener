package com.schwab.urlshortener.orchestration;

import java.util.Map;
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
}
