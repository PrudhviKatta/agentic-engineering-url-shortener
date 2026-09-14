package com.schwab.urlshortener.orchestration;

public class NoPendingApprovalException extends RuntimeException {
    public NoPendingApprovalException(String message) {
        super(message);
    }
}
