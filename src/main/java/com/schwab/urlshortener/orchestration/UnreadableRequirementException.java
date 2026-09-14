package com.schwab.urlshortener.orchestration;

// Codex REQUEST AMENDMENTS item 1 (T026a): POST /workflows' 422 case — "requirement input
// unreadable/absent" (REQ-016 safe-stop at Stage 1) — per openapi.yaml's own contract, there is no
// 501/not-implemented escape hatch for this operation; a genuinely invalid request gets a real,
// documented 4xx, not a stub response.
public class UnreadableRequirementException extends RuntimeException {
    public UnreadableRequirementException(String message) {
        super(message);
    }
}
