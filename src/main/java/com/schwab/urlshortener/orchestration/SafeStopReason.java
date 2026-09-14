package com.schwab.urlshortener.orchestration;

// Codex REQUEST AMENDMENTS items 2/6: distinguishes WHY a step/workflow safe-stopped, so
// retrySafeStopped (item 6) can resume a POLICY_BLOCKED step from the policy boundary without
// re-running its handler, while a RETRY_EXHAUSTED or COMPENSATION_FAILED step genuinely never
// completed and must actually re-run.
public enum SafeStopReason {
    POLICY_BLOCKED,
    RETRY_EXHAUSTED,
    COMPENSATION_FAILED
}
