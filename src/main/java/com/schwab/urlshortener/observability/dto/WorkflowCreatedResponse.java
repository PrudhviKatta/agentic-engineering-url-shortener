package com.schwab.urlshortener.observability.dto;

import com.schwab.urlshortener.orchestration.WorkflowStatus;

// Matches openapi.yaml's WorkflowCreatedResponse schema exactly: {id, status, correlationId, planVersion}.
public record WorkflowCreatedResponse(String id, WorkflowStatus status, String correlationId, int planVersion) { }
