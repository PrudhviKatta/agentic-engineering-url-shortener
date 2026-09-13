package com.schwab.urlshortener.orchestration;

public class UnknownDependencyException extends RuntimeException {
    public UnknownDependencyException(String message) { super(message); }
}
