package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditRecorderTest {

    @Autowired private WorkflowEventJpaRepository eventRepository;

    @Test
    void persistsCompleteEventIdentityAndEvidence() {
        AuditRecorder recorder = new AuditRecorder(eventRepository);

        WorkflowEventEntity saved = recorder.record(
                "workflow-1", "HUMAN", "reviewer-7", "approval_rejected", "design",
                "REJECTED", "missing threat model", Map.of("decision", "reject"));

        WorkflowEventEntity event = eventRepository.findById(saved.getId()).orElseThrow();
        assertThat(event.getWorkflowId()).isEqualTo("workflow-1");
        assertThat(event.getActorType()).isEqualTo("HUMAN");
        assertThat(event.getActorId()).isEqualTo("reviewer-7");
        assertThat(event.getAction()).isEqualTo("approval_rejected");
        assertThat(event.getAffectedArtifact()).isEqualTo("design");
        assertThat(event.getResult()).isEqualTo("REJECTED");
        assertThat(event.getReason()).isEqualTo("missing threat model");
        assertThat(event.getPayload()).containsEntry("decision", "reject");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void convenienceOverloadRetainsReasonAndUsesEmptyPayload() {
        AuditRecorder recorder = new AuditRecorder(eventRepository);

        WorkflowEventEntity event = recorder.record(
                "workflow-2", "SYSTEM", "resumed", "OK", "restart recovery", null);

        assertThat(event.getReason()).isEqualTo("restart recovery");
        assertThat(event.getPayload()).isEmpty();
    }
}
