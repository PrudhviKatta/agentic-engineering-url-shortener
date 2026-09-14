package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.PermanentStageException;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom dispatches stage handlers onto its internal thread pool, and Task 33's
// TimeoutExecutor wraps every attempt in a SECOND, per-attempt thread pool — both borrow different
// connections from the same DataSource, so without this override those worker threads cannot see
// the test thread's uncommitted inserts. Same root cause and fix as every other WorkflowEngine*Test
// class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineReliabilityIntegrationTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engineWithCompensation(CompensationRegistry registry) {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, null, policyExceptionRepository, registry);
    }

    @Test
    void stageRetriesThenCompensatesOnExhaustion() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 2, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        AtomicBoolean compensated = new AtomicBoolean(false);
        registry.register("flaky", result -> compensated.set(true));

        WorkflowEngine engine = engineWithCompensation(registry);

        String workflowId = engine.submit("demo", stages, "run-33a");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(step.getRetryCount()).isEqualTo(2);
        assertThat(compensated.get()).isTrue();

        // Design note (brief): a compensated failure does NOT block — the workflow proceeds
        // normally toward COMPLETED.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void exhaustionWithNoCompensationRegisteredSafeStopsTheWorkflow() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null, null, null));

        WorkflowEngine engine = engineWithCompensation(new CompensationRegistry()); // nothing registered

        String workflowId = engine.submit("demo", stages, "run-33b");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getRetryCount()).isEqualTo(1);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }

    @Test
    void permanentFailureIsNeverRetriedBeforeCompensating() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler broken = ctx -> { attempts.incrementAndGet(); throw new PermanentStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("broken", List.of(), broken, false, false, 5, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        AtomicBoolean compensated = new AtomicBoolean(false);
        registry.register("broken", result -> compensated.set(true));

        WorkflowEngine engine = engineWithCompensation(registry);
        String workflowId = engine.submit("demo", stages, "run-33c");

        assertThat(attempts.get()).isEqualTo(1); // no retries despite maxRetries=5
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "broken").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(step.getRetryCount()).isEqualTo(0); // a PermanentStageException never increments it
        assertThat(compensated.get()).isTrue();
    }

    @Test
    void aFlakyHandlerThatRecoversWithinRetryBudgetSucceedsNormally() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler eventuallyWorks = ctx -> {
            if (attempts.incrementAndGet() < 2) throw new TransientStageException("simulated");
            return Map.of("ok", true);
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), eventuallyWorks, false, false, 3, 5, null, null, null));

        WorkflowEngine engine = engineWithCompensation(new CompensationRegistry());
        String workflowId = engine.submit("demo", stages, "run-33d");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(step.getRetryCount()).isEqualTo(1); // one failed attempt before success
        assertThat(step.getResult()).isEqualTo(Map.of("ok", true));

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }
}
