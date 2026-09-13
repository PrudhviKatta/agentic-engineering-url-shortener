package com.schwab.urlshortener.orchestration;

public enum StepStatus {
    PENDING, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, RETRYING,
    ROLLED_BACK, COMPENSATED, SAFE_STOPPED, INVALIDATED, SKIPPED
}
