package com.schwab.urlshortener.orchestration;

public enum WorkflowStatus {
    CREATED, RUNNING, PENDING_APPROVAL, SAFE_STOPPED, REPLANNING, COMPLETED, FAILED
}
