package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventJpaRepository extends JpaRepository<WorkflowEventEntity, Long> {
    List<WorkflowEventEntity> findByWorkflowIdOrderById(String workflowId);
}
