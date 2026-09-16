package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalJpaRepository extends JpaRepository<ApprovalEntity, Long> {
    // Corrected 2026-09-13: plan-version-scoped — see this task's Interfaces note above.
    List<ApprovalEntity> findByWorkflowIdAndPlanVersionAndStepNameAndDecision(
            String workflowId, int planVersion, String stepName, String decision);

    // Round-6 correction (Independent review amendments item 1): stepInstanceId, not stepName, is the
    // authoritative key for "was THIS specific step instance approved" — stepName alone cannot
    // distinguish two different WorkflowStepEntity rows that happen to share a name (e.g. a
    // retired-and-reintroduced stage across plan versions, even within the same planVersion if a
    // replan ever reuses a name within one version).
    List<ApprovalEntity> findByStepInstanceIdAndDecision(Long stepInstanceId, String decision);

    // Round-6b correction (independent-review finding): "has this step instance been decided at
    // all" (approved OR rejected) — needed so a rejected exit gate is never mistaken for "still
    // needs gating" by a check that only looks for an approved decision.
    boolean existsByStepInstanceId(Long stepInstanceId);
}
