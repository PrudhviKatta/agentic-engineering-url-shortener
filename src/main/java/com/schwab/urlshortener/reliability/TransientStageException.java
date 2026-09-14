package com.schwab.urlshortener.reliability;

public class TransientStageException extends RuntimeException {
    public TransientStageException(String message) { super(message); }
}
