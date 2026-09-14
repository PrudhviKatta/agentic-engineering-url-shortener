package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approvals")
public class ApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    // Corrected 2026-09-13: scoped to a plan version and a specific step row from this task
    // onward — see this task's Interfaces note above.
    @Column(name = "plan_version")
    private int planVersion = 1;

    @Column(name = "step_instance_id")
    private Long stepInstanceId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "requested_at")
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String decision;

    @Column(name = "decided_by")
    private String decidedBy;

    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public Long getStepInstanceId() { return stepInstanceId; }
    public void setStepInstanceId(Long stepInstanceId) { this.stepInstanceId = stepInstanceId; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
