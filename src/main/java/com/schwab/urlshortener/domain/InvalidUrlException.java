package com.schwab.urlshortener.domain;

public class InvalidUrlException extends RuntimeException {
    private final String reason;

    public InvalidUrlException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
