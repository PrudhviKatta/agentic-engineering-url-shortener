package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineExecutionTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager);
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
    void submitRunsParallelGroupConcurrently() throws Exception {
        // Round-6 correction (Codex REQUEST AMENDMENTS item 5): a Thread.sleep-based ordering
        // assertion is inherently non-deterministic (a slow CI runner can let the "slow" handler
        // finish first). This proves genuine concurrency instead: both handlers must independently
        // reach bothStarted.countDown() before either is allowed past releaseGate — impossible
        // unless the engine actually runs them on separate threads at the same time, since each
        // handler blocks on releaseGate (not yet released) before returning.
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch releaseGate = new CountDownLatch(1);
        StageHandler a = ctx -> {
            bothStarted.countDown();
            await(releaseGate);
            return Map.of();
        };
        StageHandler b = ctx -> {
            bothStarted.countDown();
            await(releaseGate);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("a", List.of(), a, false, false, 0, 30, "g", null, null),
                new StageDefinition("b", List.of(), b, false, false, 0, 30, "g", null, null)
        );

        ExecutorService submitExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = submitExecutor.submit(() -> engine().submit("demo", stages, "run-2"));

            assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseGate.countDown();
            String workflowId = future.get(5, TimeUnit.SECONDS);

            assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                    .isEqualTo(WorkflowStatus.COMPLETED);
        } finally {
            submitExecutor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
