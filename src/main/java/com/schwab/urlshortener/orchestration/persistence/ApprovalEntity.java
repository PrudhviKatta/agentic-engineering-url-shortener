package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approvals",
        uniqueConstraints = @UniqueConstraint(name = "uk_approvals_step_instance_id", columnNames = "step_instance_id"))
public class ApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    // Corrected 2026-09-13: scoped to a plan version and a specific step row from this task
    // onward — see this task's Interfaces note above.
    @Column(name = "plan_version", nullable = false)
    private int planVersion = 1;

    // Round-6c correction: UNIQUE — one approval decision per step instance, enforced at the DB
    // level (data-model.md's approvals table) as a backstop to WorkflowEngine.approve()'s
    // pessimistic row lock, so a losing concurrent decision can never be persisted twice.
    @Column(name = "step_instance_id", nullable = false)
    private Long stepInstanceId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String decision;

    @Column(name = "decided_by")
    private String decidedBy;

    // Corrected 2026-09-15 (Codex round 5 finding): data-model.md Section 6 specifies
    // VARCHAR(1024) for this column ("Rationale for the decision — required by REQ-013's
    // 'recorded with rationale' language"); the entity carried no explicit length at all, so
    // Hibernate's default (255) was silently in effect instead. WorkflowEngine.approve() already
    // rejects a caller-supplied reason longer than this before it would ever reach here.
    @Column(length = 1024)
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
