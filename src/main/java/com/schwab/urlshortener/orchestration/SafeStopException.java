package com.schwab.urlshortener.orchestration;

public class SafeStopException extends RuntimeException {
    public SafeStopException(String message) {
        super(message);
    }
}
