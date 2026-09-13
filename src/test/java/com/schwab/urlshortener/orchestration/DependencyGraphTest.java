package com.schwab.urlshortener.orchestration;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphTest {

    private static Map<String, Object> noop(WorkflowContext ctx) {
        return Map.of();
    }

    @Test
    void topologicalBatchesRespectDependenciesAndGroupsIndependents() {
        List<StageDefinition> stages = List.of(
                StageDefinition.of("requirement", List.of(), DependencyGraphTest::noop),
                StageDefinition.of("design", List.of("requirement"), DependencyGraphTest::noop),
                new StageDefinition("security_scan", List.of("design"), DependencyGraphTest::noop, false, false, 0, 30, "review"),
                new StageDefinition("doc_draft", List.of("design"), DependencyGraphTest::noop, false, false, 0, 30, "review"),
                StageDefinition.of("release_readiness", List.of("security_scan", "doc_draft"), DependencyGraphTest::noop)
        );
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        List<List<StageDefinition>> batches = graph.topologicalBatches();

        assertThat(names(batches.get(0))).containsExactly("requirement");
        assertThat(names(batches.get(1))).containsExactly("design");
        assertThat(names(batches.get(2))).containsExactlyInAnyOrder("security_scan", "doc_draft");
        assertThat(names(batches.get(3))).containsExactly("release_readiness");
    }

    @Test
    void detectsCycle() {
        List<StageDefinition> stages = List.of(
                StageDefinition.of("a", List.of("b"), DependencyGraphTest::noop),
                StageDefinition.of("b", List.of("a"), DependencyGraphTest::noop)
        );
        assertThatThrownBy(() -> new DependencyGraph(stages).validate()).isInstanceOf(CycleException.class);
    }

    @Test
    void detectsUnknownDependency() {
        List<StageDefinition> stages = List.of(StageDefinition.of("a", List.of("missing"), DependencyGraphTest::noop));
        assertThatThrownBy(() -> new DependencyGraph(stages).validate()).isInstanceOf(UnknownDependencyException.class);
    }

    private static List<String> names(List<StageDefinition> batch) {
        return batch.stream().map(StageDefinition::name).toList();
    }
}
