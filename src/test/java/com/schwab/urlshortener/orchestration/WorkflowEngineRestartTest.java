package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.ApprovalJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineRestartTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void freshEngineRecoversAbandonedRunningStepAndContinuesWithinFiveSeconds() {
        String workflowId = "restart-" + java.util.UUID.randomUUID();
        persistInstance(workflowId, WorkflowStatus.RUNNING);
        WorkflowStepEntity first = persistStep(workflowId, "first", List.of(), StepStatus.RUNNING, 0);
        first.setStartedAt(Instant.now());
        stepRepository.save(first);
        persistStep(workflowId, "second", List.of("first"), StepStatus.PENDING, 1);

        StageCatalog catalog = new StageCatalog(
                Map.of("first", ctx -> Map.of("recovered", true), "second", ctx -> Map.of("done", true)),
                Map.of(), Map.of());
        WorkflowEngine freshEngine = engine(catalog);

        Instant started = Instant.now();
        freshEngine.runFrom(workflowId);

        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(5));
        assertThat(stepRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(stepRepository.findById(first.getId()).orElseThrow().getRetryCount()).isZero();
        assertThat(stepRepository.findByWorkflowIdAndNameAndStatusNot(workflowId, "second", StepStatus.INVALIDATED)
                .orElseThrow().getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .map(WorkflowEventEntity::getAction)).contains("recovered_abandoned_running:first", "resumed:" + workflowId);
    }

    @Test
    void freshEngineCanApproveAndResumeWithoutAnySubmissionState() {
        AtomicBoolean ran = new AtomicBoolean();
        StageHandler gated = ctx -> { ran.set(true); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gated, true, false, 0, 5, null, null, null));
        StageCatalog catalog = new StageCatalog(Map.of("gate", gated), Map.of(), Map.of());
        String workflowId = engine(catalog).submit("restart-gate", stages, "restart-gate-1");

        assertThat(ran).isFalse();
        WorkflowEngine freshEngine = engine(catalog);
        freshEngine.approve(workflowId, "gate", "approved", "human", "resume after restart");

        assertThat(ran).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void inactiveStageIsSkippedAndItsDependentStillRuns() {
        AtomicBoolean conditionalRan = new AtomicBoolean();
        AtomicBoolean dependentRan = new AtomicBoolean();
        StageHandler conditional = ctx -> { conditionalRan.set(true); return Map.of(); };
        StageHandler dependent = ctx -> { dependentRan.set(true); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("conditional", List.of(), conditional, false, false, 2, 7,
                        "conditional-group", "only_when_needed", null),
                StageDefinition.of("dependent", List.of("conditional"), dependent));
        StageCatalog catalog = new StageCatalog(
                Map.of("conditional", conditional, "dependent", dependent),
                Map.of("only_when_needed", ctx -> false), Map.of());

        String workflowId = engine(catalog).submit("conditional", stages, "conditional-1");

        WorkflowStepEntity skipped = stepRepository
                .findByWorkflowIdAndNameAndStatusNot(workflowId, "conditional", StepStatus.INVALIDATED).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(StepStatus.SKIPPED);
        assertThat(skipped.getEndedAt()).isNotNull();
        assertThat(skipped.getMaxRetries()).isEqualTo(2);
        assertThat(skipped.getTimeoutSeconds()).isEqualTo(7);
        assertThat(skipped.getParallelGroup()).isEqualTo("conditional-group");
        assertThat(skipped.getActivationConditionKey()).isEqualTo("only_when_needed");
        assertThat(conditionalRan).isFalse();
        assertThat(dependentRan).isTrue();
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.COMPLETED);
    }

    private WorkflowEngine engine(StageCatalog catalog) {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, null, null, null, null, catalog);
    }

    private void persistInstance(String workflowId, WorkflowStatus status) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature("restart");
        instance.setStatus(status);
        instance.setCorrelationId(workflowId);
        instance.setPlanVersion(1);
        instance.setContextJson(Map.of());
        instanceRepository.save(instance);
    }

    private WorkflowStepEntity persistStep(String workflowId, String name, List<String> dependencies,
            StepStatus status, int sequence) {
        WorkflowStepEntity step = new WorkflowStepEntity();
        step.setWorkflowId(workflowId);
        step.setPlanVersion(1);
        step.setName(name);
        step.setSequence(sequence);
        step.setStatus(status);
        step.setDependsOn(dependencies);
        step.setMaxRetries(0);
        step.setTimeoutSeconds(5);
        return stepRepository.save(step);
    }
}
