package com.schwab.urlshortener.api;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowControllerContractTest {

    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    // Corrected 2026-09-13: autowire the shared @Component WorkflowEngine bean — the SAME
    // instance WorkflowController holds — rather than constructing a private one. Using a
    // separate `new WorkflowEngine(...)` here would prove nothing about the actual bug this
    // task fixes (WorkflowController seeing workflows submitted elsewhere in the same process).
    @Autowired private WorkflowEngine engine;

    private String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void workflowStateAndAuditEndpointsExposeRealPersistedState() {
        // No generic "create workflow" HTTP endpoint exists (workflows are always
        // created from a scenario's specific stage set — Phase 9); trigger one
        // directly through the shared engine the same way ScenarioRunner does, then
        // exercise the read-only HTTP surface against it.
        List<StageDefinition> stages = List.of(StageDefinition.of("only", List.of(), ctx -> Map.of("ok", true)));
        String workflowId = engine.submit("demo", stages, "contract-test");

        ResponseEntity<Map> stateResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(stateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(stateResp.getBody().get("status")).isEqualTo("COMPLETED");
        List<?> steps = (List<?>) stateResp.getBody().get("steps");
        assertThat(steps).hasSize(1);

        ResponseEntity<List> auditResp = rest.getForEntity(url("/workflows/" + workflowId + "/audit"), List.class);
        assertThat(auditResp.getStatusCode().value()).isEqualTo(200);
        assertThat(auditResp.getBody()).isNotEmpty();
    }

    @Test
    void approvalEndpointResumesAWorkflowSubmittedThroughTheSameSharedEngineBean() {
        // Proves the cross-component engine-sharing fix: the workflow is submitted here
        // (simulating ScenarioRunner submitting it), but resumed entirely through the HTTP
        // approval endpoint (simulating a separate WorkflowController-driven caller) — this
        // only works if both go through the SAME WorkflowEngine bean and its registry.
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        String workflowId = engine.submit("demo", stages, "contract-test-2");

        ResponseEntity<Map> beforeResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(beforeResp.getBody().get("status")).isEqualTo("PENDING_APPROVAL");

        ResponseEntity<Void> approveResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "approved", "actor", "human", "reason", "looks safe"),
                Void.class);
        assertThat(approveResp.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<Map> afterResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(afterResp.getBody().get("status")).isEqualTo("COMPLETED");
    }
}
