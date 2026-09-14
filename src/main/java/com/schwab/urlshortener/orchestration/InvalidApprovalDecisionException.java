package com.schwab.urlshortener.orchestration;

public class InvalidApprovalDecisionException extends RuntimeException {
    public InvalidApprovalDecisionException(String message) {
        super(message);
    }
}
