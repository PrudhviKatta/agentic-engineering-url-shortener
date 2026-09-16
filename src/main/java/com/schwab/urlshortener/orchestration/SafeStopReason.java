package com.schwab.urlshortener.orchestration;

// Independent review amendments items 2/6: distinguishes WHY a step/workflow safe-stopped, so
// retrySafeStopped (item 6) can resume a POLICY_BLOCKED step from the policy boundary without
// re-running its handler, while a RETRY_EXHAUSTED or COMPENSATION_FAILED step genuinely never
// completed and must actually re-run.
public enum SafeStopReason {
    POLICY_BLOCKED,
    RETRY_EXHAUSTED,
    COMPENSATION_FAILED,
    // Independent review amendments follow-up (Section 17 review): a handler exception outside the
    // Permanent/TransientStageException hierarchy (a genuine bug — NPE, IllegalStateException,
    // etc.) that RetryExecutor/TimeoutExecutor don't classify. Resolved the same way
    // RETRY_EXHAUSTED is (genuinely re-run on resume — the handler never actually completed),
    // tagged distinctly so the audit trail and reason logs don't mislabel an unexpected bug as an
    // ordinary retry exhaustion.
    UNEXPECTED_FAILURE
}
