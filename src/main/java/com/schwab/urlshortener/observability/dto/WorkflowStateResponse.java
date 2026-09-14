package com.schwab.urlshortener.observability.dto;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import java.time.Instant;
import java.util.List;

public record WorkflowStateResponse(String id, WorkflowStatus status, List<StepView> steps) {
    public record StepView(String name, StepStatus status, Instant startedAt, Instant endedAt, int retryCount) { }
}
