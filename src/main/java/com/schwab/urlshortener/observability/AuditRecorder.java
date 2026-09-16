package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class AuditRecorder {

    private final WorkflowEventJpaRepository eventRepository;

    public AuditRecorder(WorkflowEventJpaRepository eventRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
    }

    public WorkflowEventEntity record(String workflowId, String actorType, String actorId,
                                      String action, String affectedArtifact, String result,
                                      String reason, Map<String, Object> payload) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(Objects.requireNonNull(workflowId, "workflowId"));
        event.setActorType(Objects.requireNonNull(actorType, "actorType"));
        event.setActorId(actorId);
        event.setAction(Objects.requireNonNull(action, "action"));
        event.setAffectedArtifact(affectedArtifact);
        event.setResult(Objects.requireNonNull(result, "result"));
        event.setReason(reason);
        event.setPayload(payload == null ? Map.of() : Map.copyOf(payload));
        return eventRepository.save(event);
    }

    public WorkflowEventEntity record(String workflowId, String actorType, String action,
                                      String result, String reason, Map<String, Object> payload) {
        return record(workflowId, actorType, null, action, null, result, reason, payload);
    }
}
