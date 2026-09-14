package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalJpaRepository extends JpaRepository<ApprovalEntity, Long> {
    // Corrected 2026-09-13: plan-version-scoped — see this task's Interfaces note above.
    List<ApprovalEntity> findByWorkflowIdAndPlanVersionAndStepNameAndDecision(
            String workflowId, int planVersion, String stepName, String decision);
}
