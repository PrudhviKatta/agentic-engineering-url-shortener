package com.schwab.urlshortener.orchestration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceJpaRepository extends JpaRepository<WorkflowInstanceEntity, String> { }
