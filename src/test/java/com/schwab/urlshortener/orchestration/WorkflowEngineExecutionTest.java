package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineExecutionTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository);
    }

    @Test
    void submitRunsSequentialStagesToCompletion() {
        List<String> calls = new CopyOnWriteArrayList<>();
        StageHandler stepA = ctx -> { calls.add("a"); return Map.of("ok", true); };
        StageHandler stepB = ctx -> { calls.add("b"); return Map.of("ok", true); };
        List<StageDefinition> stages = List.of(
                StageDefinition.of("a", List.of(), stepA),
                StageDefinition.of("b", List.of("a"), stepB)
        );

        String workflowId = engine().submit("demo", stages, "run-1");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(calls).containsExactly("a", "b");
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionOrderBySequence(workflowId, 1))
                .allMatch(s -> s.getStatus() == StepStatus.SUCCEEDED);
    }

    @Test
    void submitRunsParallelGroupConcurrently() {
        List<String> order = new CopyOnWriteArrayList<>();
        StageHandler slow = ctx -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            order.add("slow");
            return Map.of();
        };
        StageHandler fast = ctx -> { order.add("fast"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("slow", List.of(), slow, false, false, 0, 30, "g"),
                new StageDefinition("fast", List.of(), fast, false, false, 0, 30, "g")
        );

        engine().submit("demo", stages, "run-2");

        assertThat(order.get(0)).isEqualTo("fast"); // fast finishes first despite being submitted alongside slow
    }
}
