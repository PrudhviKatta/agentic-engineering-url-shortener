package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MetricsServiceIsolationTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;

    @Test
    void perWorkflowSummaryDoesNotIncludeAnotherWorkflowsSteps() {
        saveInstance("wf-a", WorkflowStatus.FAILED, 10);
        saveStep("wf-a", "retried", StepStatus.COMPENSATED, 2, true);

        saveInstance("wf-b", WorkflowStatus.COMPLETED, 4);
        saveStep("wf-b", "clean", StepStatus.SUCCEEDED, 0, true);
        saveStep("wf-b", "not-started", StepStatus.PENDING, 0, false);

        ReliabilitySummary summary = MetricsService.computeReliabilitySummary(
                instanceRepository, stepRepository, "wf-b");

        assertThat(summary.successRate()).isEqualTo(1.0);
        assertThat(summary.failureRate()).isZero();
        assertThat(summary.retryFrequency()).isZero();
        assertThat(summary.rollbackOrCompensationFrequency()).isZero();
        assertThat(summary.fallbackFrequency()).isZero();
        assertThat(summary.endToEndLatencySeconds()).isEqualTo(4.0);
        assertThat(summary.isDemonstrationData()).isTrue();
    }

    private void saveInstance(String id, WorkflowStatus status, long latencySeconds) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(id);
        instance.setFeature(id);
        instance.setStatus(status);
        instance.setCorrelationId("correlation-" + id);
        instance.setUpdatedAt(instance.getCreatedAt().plusSeconds(latencySeconds));
        instanceRepository.save(instance);
    }

    private void saveStep(String workflowId, String name, StepStatus status, int retryCount,
                          boolean executed) {
        WorkflowStepEntity step = new WorkflowStepEntity();
        step.setWorkflowId(workflowId);
        step.setName(name);
        step.setStatus(status);
        step.setSequence(1);
        step.setDependsOn(List.of());
        step.setRetryCount(retryCount);
        if (executed) {
            step.setStartedAt(Instant.parse("2026-01-01T00:00:00Z"));
        }
        stepRepository.save(step);
    }
}
