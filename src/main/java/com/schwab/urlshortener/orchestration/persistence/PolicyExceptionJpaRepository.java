package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyExceptionJpaRepository extends JpaRepository<PolicyExceptionEntity, Long> {
    List<PolicyExceptionEntity> findByWorkflowIdAndPolicyId(String workflowId, String policyId);
}
