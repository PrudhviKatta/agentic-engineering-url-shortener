package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStepJpaRepository extends JpaRepository<WorkflowStepEntity, Long> {
    // Corrected 2026-09-13: plan-version-scoped from this task onward — never a bare
    // (workflowId, name) lookup. See this task's Interfaces note above.
    Optional<WorkflowStepEntity> findByWorkflowIdAndPlanVersionAndName(String workflowId, int planVersion, String name);
    List<WorkflowStepEntity> findByWorkflowIdAndPlanVersionOrderBySequence(String workflowId, int planVersion);
}
