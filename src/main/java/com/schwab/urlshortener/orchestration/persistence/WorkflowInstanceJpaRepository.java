package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkflowInstanceJpaRepository extends JpaRepository<WorkflowInstanceEntity, String> {
    // Independent review amendments item 7: pessimistic row lock (SELECT ... FOR UPDATE) on the
    // WORKFLOW instance, not just the step. Round-6c's step-level lock only serializes two
    // decisions on the SAME step; two DIFFERENT exit-gated siblings decided truly simultaneously
    // (reject one, approve the other) could each pass the terminal-workflow check before either
    // commits, letting the approved sibling's runFrom() resurrect/complete a workflow a concurrent
    // rejection should have failed. Locking the instance row too closes that race: only one
    // approve() call for a given workflow can be inside its terminal-status check at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowInstanceEntity> findWithLockById(String id);
}
