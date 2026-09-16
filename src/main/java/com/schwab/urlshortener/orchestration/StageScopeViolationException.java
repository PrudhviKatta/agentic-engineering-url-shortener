package com.schwab.urlshortener.orchestration;

public final class StageScopeViolationException extends RuntimeException {

    public StageScopeViolationException(String message) {
        super(message);
    }
}
