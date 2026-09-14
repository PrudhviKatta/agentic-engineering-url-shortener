package com.schwab.urlshortener.reliability;

public class StageTimeoutException extends RuntimeException {
    public StageTimeoutException(String message) { super(message); }
}
