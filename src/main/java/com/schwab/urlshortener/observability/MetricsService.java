package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MetricsService {

    private static final String POPULATION_NOTE =
            "Demonstration data from a small, non-statistically-representative local test population; "
                    + "not production statistics or an SLA.";

    private MetricsService() { }

    public static MttrReport computeMttr(List<RecoveryEvent> events) {
        Objects.requireNonNull(events, "events must not be null");

        int unrecoveredCount = 0;
        int excludedCount = 0;
        int recoveredCount = 0;
        long totalRecoveryMillis = 0;

        for (RecoveryEvent event : events) {
            if (event == null) {
                excludedCount++;
                continue;
            }
            if (!event.recovered()) {
                unrecoveredCount++;
                continue;
            }
            if (event.recoveryStartAt() == null || event.recoveryCompleteAt() == null
                    || event.recoveryCompleteAt().isBefore(event.recoveryStartAt())) {
                excludedCount++;
                continue;
            }
            recoveredCount++;
            totalRecoveryMillis += Duration.between(
                    event.recoveryStartAt(), event.recoveryCompleteAt()).toMillis();
        }

        Double mttrSeconds = recoveredCount == 0
                ? null
                : totalRecoveryMillis / 1000.0 / recoveredCount;
        return new MttrReport(mttrSeconds, recoveredCount, unrecoveredCount, excludedCount,
                POPULATION_NOTE);
    }

    public static ReliabilitySummary computeReliabilitySummary(
            WorkflowInstanceJpaRepository instanceRepository,
            WorkflowStepJpaRepository stepRepository,
            String workflowId) {
        return computeReliabilitySummary(instanceRepository, stepRepository, null, workflowId);
    }

    public static ReliabilitySummary computeReliabilitySummary(
            WorkflowInstanceJpaRepository instanceRepository,
            WorkflowStepJpaRepository stepRepository,
            WorkflowEventJpaRepository eventRepository,
            String workflowId) {
        Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        Objects.requireNonNull(stepRepository, "stepRepository must not be null");

        List<WorkflowInstanceEntity> instances = workflowId == null
                ? instanceRepository.findAll()
                : instanceRepository.findById(workflowId).map(List::of).orElseGet(List::of);
        List<WorkflowStepEntity> steps = workflowId == null
                ? stepRepository.findAll()
                : stepRepository.findByWorkflowId(workflowId);

        List<WorkflowInstanceEntity> terminalInstances = instances.stream()
                .filter(instance -> isTerminal(instance.getStatus()))
                .toList();
        long succeeded = terminalInstances.stream()
                .filter(instance -> instance.getStatus() == WorkflowStatus.COMPLETED)
                .count();
        long failed = terminalInstances.stream()
                .filter(instance -> instance.getStatus() == WorkflowStatus.FAILED
                        || instance.getStatus() == WorkflowStatus.SAFE_STOPPED)
                .count();

        List<WorkflowStepEntity> executedSteps = steps.stream()
                .filter(step -> step.getStartedAt() != null)
                .toList();
        long retriedSteps = executedSteps.stream()
                .filter(step -> step.getRetryCount() > 0)
                .count();
        List<WorkflowStepEntity> failedAtLeastOnce = executedSteps.stream()
                .filter(MetricsService::failedAtLeastOnce)
                .toList();
        long rollbackOrCompensationSteps = failedAtLeastOnce.stream()
                .filter(step -> step.getStatus() == StepStatus.ROLLED_BACK
                        || step.getStatus() == StepStatus.COMPENSATED)
                .count();
        long fallbackSteps = failedAtLeastOnce.stream()
                .filter(step -> "SUCCEEDED_WITH_FALLBACK".equals(step.getStatus().name()))
                .count();

        Double latency = terminalInstances.stream()
                .filter(instance -> instance.getCreatedAt() != null && instance.getUpdatedAt() != null)
                .mapToDouble(instance -> Duration.between(
                        instance.getCreatedAt(), instance.getUpdatedAt()).toMillis() / 1000.0)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        List<WorkflowEventEntity> auditEvents = eventRepository == null
                ? List.of()
                : workflowId == null
                        ? eventRepository.findAll()
                        : eventRepository.findByWorkflowIdOrderById(workflowId);
        MttrReport mttr = computeMttr(recoveryEventsFromAudit(auditEvents));

        return new ReliabilitySummary(
                frequency(succeeded, terminalInstances.size()),
                frequency(failed, terminalInstances.size()),
                frequency(retriedSteps, executedSteps.size()),
                frequency(rollbackOrCompensationSteps, failedAtLeastOnce.size()),
                frequency(fallbackSteps, failedAtLeastOnce.size()),
                latency,
                mttr,
                true);
    }

    static List<RecoveryEvent> recoveryEventsFromAudit(List<WorkflowEventEntity> events) {
        record OpenRecovery(Instant detectedAt, Instant startedAt, String mechanism) { }

        List<WorkflowEventEntity> ordered = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getTimestamp() != null)
                .sorted(java.util.Comparator.comparing(WorkflowEventEntity::getTimestamp)
                        .thenComparing(event -> event.getId() == null ? Long.MAX_VALUE : event.getId()))
                .toList();
        Map<String, OpenRecovery> openByWorkflow = new HashMap<>();
        List<RecoveryEvent> recoveries = new ArrayList<>();

        for (WorkflowEventEntity event : ordered) {
            String action = event.getAction() == null ? "" : event.getAction();
            String workflowId = event.getWorkflowId();
            if (workflowId == null || event.getTimestamp() == null) {
                continue;
            }
            if (isRecoveryStart(action)) {
                openByWorkflow.putIfAbsent(workflowId,
                        new OpenRecovery(event.getTimestamp(), event.getTimestamp(), mechanism(action)));
            }
            if (isRecoveryCompletion(action)) {
                OpenRecovery open = openByWorkflow.remove(workflowId);
                if (open != null) {
                    recoveries.add(new RecoveryEvent(open.detectedAt(), open.startedAt(),
                            event.getTimestamp(), true, open.mechanism()));
                }
            }
        }
        openByWorkflow.values().forEach(open -> recoveries.add(new RecoveryEvent(
                open.detectedAt(), open.startedAt(), null, false, open.mechanism())));
        return List.copyOf(recoveries);
    }

    private static boolean isRecoveryStart(String action) {
        return action.equals("fallback_started")
                || action.equals("safe_stop_triggered")
                || action.startsWith("compensation_failed:")
                || action.startsWith("recovered_abandoned_running:");
    }

    private static boolean isRecoveryCompletion(String action) {
        return action.equals("fallback_succeeded")
                || action.startsWith("compensation_ran:")
                || action.equals("workflow_completed");
    }

    private static String mechanism(String action) {
        if (action.startsWith("fallback")) return "fallback";
        if (action.startsWith("compensation")) return "compensation";
        if (action.startsWith("recovered_abandoned")) return "restart";
        return "operator_resume";
    }

    private static boolean isTerminal(WorkflowStatus status) {
        return status == WorkflowStatus.COMPLETED
                || status == WorkflowStatus.FAILED
                || status == WorkflowStatus.SAFE_STOPPED;
    }

    private static boolean failedAtLeastOnce(WorkflowStepEntity step) {
        if (step.getRetryCount() > 0) {
            return true;
        }
        return switch (step.getStatus()) {
            case FAILED, SAFE_STOPPED, ROLLED_BACK, COMPENSATED, SUCCEEDED_WITH_FALLBACK -> true;
            default -> false;
        };
    }

    private static double frequency(long numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
