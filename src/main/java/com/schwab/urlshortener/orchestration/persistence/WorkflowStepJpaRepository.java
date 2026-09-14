package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkflowStepJpaRepository extends JpaRepository<WorkflowStepEntity, Long> {
    // Corrected 2026-09-13: plan-version-scoped from this task onward — never a bare
    // (workflowId, name) lookup. See this task's Interfaces note above.
    Optional<WorkflowStepEntity> findByWorkflowIdAndPlanVersionAndName(String workflowId, int planVersion, String name);
    List<WorkflowStepEntity> findByWorkflowIdAndPlanVersionOrderBySequence(String workflowId, int planVersion);

    // Round-6c correction: pessimistic row lock (SELECT ... FOR UPDATE) for the specific step
    // WorkflowEngine.approve() is about to decide. Serializes concurrent approve() calls targeting
    // the SAME step instance so exactly one observes WAITING_APPROVAL and wins; the loser's read
    // (blocked until the winner's transaction commits) sees the already-decided status and is
    // rejected as "no pending gate" — see WorkflowEngine's recordDecisionAtomically().
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowStepEntity> findWithLockByWorkflowIdAndPlanVersionAndName(String workflowId, int planVersion, String name);

    // Task 49: full-graph reconstruction for StageCatalog-based restart-safe resumption — no
    // ordering guarantee needed here (callers reconstruct the dependency graph from depends_on).
    List<WorkflowStepEntity> findByWorkflowIdAndPlanVersion(String workflowId, int planVersion);
}
