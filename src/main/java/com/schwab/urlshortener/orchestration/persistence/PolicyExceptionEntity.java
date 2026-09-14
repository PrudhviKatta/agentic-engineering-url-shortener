package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "policy_exceptions")
public class PolicyExceptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "policy_id")
    private String policyId;

    private String reason;

    private String scope;

    @Column(name = "approving_authority")
    private String approvingAuthority;

    @Column(name = "compensating_control")
    private String compensatingControl;

    @Column(name = "approved_at")
    private Instant approvedAt;

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
