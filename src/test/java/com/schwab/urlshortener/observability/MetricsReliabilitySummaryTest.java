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
class MetricsReliabilitySummaryTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository eventRepository;

    @Test
    void usesTerminalExecutedAndFailedPopulationsForEachFrequency() {
        saveInstance("complete", WorkflowStatus.COMPLETED, 2);
        saveInstance("failed", WorkflowStatus.FAILED, 4);
        saveInstance("stopped", WorkflowStatus.SAFE_STOPPED, 6);
        saveInstance("running", WorkflowStatus.RUNNING, 100);

        saveStep("complete", "clean", StepStatus.SUCCEEDED, 0, true);
        saveStep("complete", "retried-success", StepStatus.SUCCEEDED, 1, true);
        saveStep("failed", "compensated", StepStatus.COMPENSATED, 0, true);
        saveStep("stopped", "safe-stop", StepStatus.SAFE_STOPPED, 0, true);
        saveStep("running", "pending", StepStatus.PENDING, 0, false);

        ReliabilitySummary summary = MetricsService.computeReliabilitySummary(
                instanceRepository, stepRepository, null);

        assertThat(summary.successRate()).isEqualTo(1.0 / 3.0);
        assertThat(summary.failureRate()).isEqualTo(2.0 / 3.0);
        assertThat(summary.retryFrequency()).isEqualTo(1.0 / 4.0);
        assertThat(summary.rollbackOrCompensationFrequency()).isEqualTo(1.0 / 3.0);
        assertThat(summary.fallbackFrequency()).isZero();
        assertThat(summary.endToEndLatencySeconds()).isEqualTo(4.0);
        assertThat(summary.mttr().recoveredCount()).isZero();
        assertThat(summary.isDemonstrationData()).isTrue();
    }

    @Test
    void emptyPopulationProducesZeroRatesAndNoLatency() {
        ReliabilitySummary summary = MetricsService.computeReliabilitySummary(
                instanceRepository, stepRepository, null);

        assertThat(summary.successRate()).isZero();
        assertThat(summary.failureRate()).isZero();
        assertThat(summary.retryFrequency()).isZero();
        assertThat(summary.rollbackOrCompensationFrequency()).isZero();
        assertThat(summary.fallbackFrequency()).isZero();
        assertThat(summary.endToEndLatencySeconds()).isNull();
        assertThat(summary.mttr().mttrSeconds()).isNull();
        assertThat(summary.isDemonstrationData()).isTrue();
    }

    @Test
    void derivesRecoveredAndUnrecoveredMttrPopulationFromPersistedAuditEvents() {
        com.schwab.urlshortener.observability.AuditRecorder recorder =
                new com.schwab.urlshortener.observability.AuditRecorder(eventRepository);
        recorder.record("recovered", "orchestrator", "workflow-engine", "fallback_started",
                "documentation", "STARTED", "primary handler failed", null);
        recorder.record("recovered", "orchestrator", "workflow-engine", "fallback_succeeded",
                "documentation", "OK", "fallback completed", null);
        recorder.record("unrecovered", "orchestrator", "workflow-engine", "safe_stop_triggered",
                null, "SAFE_STOPPED", "operator action required", null);

        ReliabilitySummary summary = MetricsService.computeReliabilitySummary(
                instanceRepository, stepRepository, eventRepository, null);

        assertThat(summary.mttr().recoveredCount()).isEqualTo(1);
        assertThat(summary.mttr().unrecoveredCount()).isEqualTo(1);
        assertThat(summary.mttr().mttrSeconds()).isNotNull().isGreaterThanOrEqualTo(0.0);
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
