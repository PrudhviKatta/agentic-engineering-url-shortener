package com.schwab.urlshortener.observability.dto;

import java.time.Instant;
import java.util.Map;

// Round-6 correction (Codex REQUEST AMENDMENTS item 3): brought into exact conformance with
// openapi.yaml's AuditEventResponse schema — workflowId, actorId, affectedArtifact, and payload
// were missing entirely.
public record AuditEventResponse(
        String workflowId,
        String actorType,
        String actorId,
        String action,
        Instant timestamp,
        String affectedArtifact,
        Map<String, Object> payload,
        String result,
        String reason
) { }
