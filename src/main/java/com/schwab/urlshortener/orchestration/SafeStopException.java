package com.schwab.urlshortener.orchestration;

public class SafeStopException extends RuntimeException {

    // Independent review amendments items 2/6: defaults to RETRY_EXHAUSTED for callers that don't
    // specify a reason (the single-arg constructor existed before item 6 introduced this
    // distinction) — the two-arg constructor is the one every current throw site actually uses.
    private final SafeStopReason reason;

    public SafeStopException(String message) {
        this(message, SafeStopReason.RETRY_EXHAUSTED);
    }

    public SafeStopException(String message, SafeStopReason reason) {
        super(message);
        this.reason = reason;
    }

    public SafeStopReason reason() {
        return reason;
    }
}
