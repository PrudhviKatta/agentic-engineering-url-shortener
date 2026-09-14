package com.schwab.urlshortener.observability.dto;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// Round-6 correction (Codex REQUEST AMENDMENTS item 3): brought into exact conformance with
// openapi.yaml's WorkflowStateResponse schema — correlationId, planVersion, and decisionLineage
// were missing entirely; each StepView was missing inputs/outputs.
public record WorkflowStateResponse(
        String id,
        WorkflowStatus status,
        String correlationId,
        int planVersion,
        List<DecisionLineageEntry> decisionLineage,
        List<StepView> steps
) {
    public record DecisionLineageEntry(String actor, String decision, String rationale, Instant recordedAt) { }

    public record StepView(
            String name,
            StepStatus status,
            Instant startedAt,
            Instant endedAt,
            int retryCount,
            Map<String, Object> inputs,
            Map<String, Object> outputs
    ) { }
}
