package com.schwab.urlshortener.orchestration;

public class CycleException extends RuntimeException {
    public CycleException(String message) { super(message); }
}
