package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.observability.AuditRecorder;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import java.util.Map;
import java.util.Objects;

/**
 * Narrow persistence capability for a stage handler. A handler receives authority over one
 * persisted step row, never over the workflow repository as a whole.
 */
public final class StageScopedStepCapability {

    private final String workflowId;
    private final String stageName;
    private final long allowedStepId;
    private final WorkflowStepJpaRepository stepRepository;
    private final AuditRecorder auditRecorder;

    public StageScopedStepCapability(String workflowId, String stageName, long allowedStepId,
                                     WorkflowStepJpaRepository stepRepository,
                                     AuditRecorder auditRecorder) {
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId");
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.allowedStepId = allowedStepId;
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder");
    }

    public WorkflowStepEntity updateStatus(long targetStepId, StepStatus status) {
        Objects.requireNonNull(status, "status");
        if (targetStepId != allowedStepId) {
            reject(targetStepId, "stage may mutate only its own workflow step");
        }

        WorkflowStepEntity target = stepRepository.findById(targetStepId)
                .orElseThrow(() -> new IllegalArgumentException("unknown workflow step: " + targetStepId));
        if (!workflowId.equals(target.getWorkflowId())) {
            reject(targetStepId, "target workflow does not match the scoped workflow");
        }
        target.setStatus(status);
        return stepRepository.save(target);
    }

    private void reject(long targetStepId, String reason) {
        auditRecorder.record(workflowId, "agent", stageName, "stage_scope_violation:update_step_status",
                Long.toString(targetStepId), "REJECTED", reason,
                Map.of("allowedStepId", allowedStepId, "targetStepId", targetStepId));
        throw new StageScopeViolationException(reason + ": " + targetStepId);
    }
}
