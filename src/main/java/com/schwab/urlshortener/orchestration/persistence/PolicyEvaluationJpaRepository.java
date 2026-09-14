package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyEvaluationJpaRepository extends JpaRepository<PolicyEvaluationEntity, Long> {
    List<PolicyEvaluationEntity> findByWorkflowIdAndPolicyId(String workflowId, String policyId);
}
