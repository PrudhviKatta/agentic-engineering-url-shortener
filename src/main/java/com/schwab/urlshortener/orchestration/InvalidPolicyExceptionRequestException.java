package com.schwab.urlshortener.orchestration;

// Independent review amendments item 4: thrown by WorkflowEngine.grantPolicyException when the request
// violates a precondition (unknown workflow/policy, a required field missing/blank, or a
// non-future expiry) — kept distinct from InvalidApprovalDecisionException/NoPendingApprovalException
// since this is a different concern (a policy-exception request, not an approval decision).
public class InvalidPolicyExceptionRequestException extends RuntimeException {
    public InvalidPolicyExceptionRequestException(String message) {
        super(message);
    }
}
