package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "policy_exceptions")
public class PolicyExceptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Codex REQUEST AMENDMENTS item 4: data-model.md's approved schema marks EVERY column on this
    // table "No" nullable (unlike approvals' partial nullability) — every field here is required
    // by design, not just by application-level convention.
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String scope;

    @Column(name = "approving_authority", nullable = false)
    private String approvingAuthority;

    @Column(name = "compensating_control", nullable = false)
    private String compensatingControl;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(nullable = false)
    private Instant expiry;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public void setApprovingAuthority(String approvingAuthority) { this.approvingAuthority = approvingAuthority; }
    public String getCompensatingControl() { return compensatingControl; }
    public void setCompensatingControl(String compensatingControl) { this.compensatingControl = compensatingControl; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getExpiry() { return expiry; }
    public void setExpiry(Instant expiry) { this.expiry = expiry; }
}
