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
        // Round-6 correction (Codex REQUEST AMENDMENTS item 3): openapi.yaml's
        // WorkflowStateResponse requires correlationId, planVersion, and decisionLineage.
        assertThat(stateResp.getBody().get("correlationId")).isEqualTo("contract-test");
        assertThat(stateResp.getBody().get("planVersion")).isEqualTo(1);
        assertThat(stateResp.getBody()).containsKey("decisionLineage");
        List<?> steps = (List<?>) stateResp.getBody().get("steps");
        assertThat(steps).hasSize(1);
        Map<String, Object> onlyStep = (Map<String, Object>) steps.get(0);
        assertThat(onlyStep).containsKeys("inputs", "outputs");
        assertThat((Map<String, Object>) onlyStep.get("outputs")).containsEntry("ok", true);

        ResponseEntity<List> auditResp = rest.getForEntity(url("/workflows/" + workflowId + "/audit"), List.class);
        assertThat(auditResp.getStatusCode().value()).isEqualTo(200);
        assertThat(auditResp.getBody()).isNotEmpty();
        Map<String, Object> firstEvent = (Map<String, Object>) auditResp.getBody().get(0);
        assertThat(firstEvent.get("workflowId")).isEqualTo(workflowId);
        assertThat(firstEvent).containsKeys("actorType", "actorId", "action", "timestamp", "result");
    }

    @Test
    void unknownWorkflowIdReturns404OnStateAndAuditEndpoints() {
        ResponseEntity<Map> stateResp = rest.getForEntity(url("/workflows/does-not-exist"), Map.class);
        assertThat(stateResp.getStatusCode().value()).isEqualTo(404);
        // Round-6b correction (independent-review finding): this used to be a bare
        // ResponseStatusException, rendered as Spring's default {timestamp,status,error,path}
        // body rather than openapi.yaml's ErrorResponse{error:{reason}} shape.
        assertThat(stateResp.getBody()).containsKey("error");
        assertThat((Map<String, Object>) stateResp.getBody().get("error")).containsKey("reason");

        ResponseEntity<Map> auditResp = rest.getForEntity(url("/workflows/does-not-exist/audit"), Map.class);
        assertThat(auditResp.getStatusCode().value()).isEqualTo(404);
        assertThat(auditResp.getBody()).containsKey("error");
    }

    @Test
    void decisionLineageReflectsActualRecordedDecisions() {
        List<StageDefinition> stages = List.of(
                StageDefinition.of("propose", List.of(), ctx -> {
                    ctx.recordDecision("agent", "propose", "initial design looks sound");
                    return Map.of();
                }));
        String workflowId = engine.submit("demo", stages, "contract-test-5");

        ResponseEntity<Map> stateResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        List<Map<String, Object>> lineage = (List<Map<String, Object>>) stateResp.getBody().get("decisionLineage");
        assertThat(lineage).hasSize(1);
        assertThat(lineage.get(0).get("actor")).isEqualTo("agent");
        assertThat(lineage.get(0).get("decision")).isEqualTo("propose");
        assertThat(lineage.get(0).get("rationale")).isEqualTo("initial design looks sound");
        assertThat(lineage.get(0).get("recordedAt")).isNotNull();
    }

    @Test
    void invalidApprovalDecisionReturns400() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        String workflowId = engine.submit("demo", stages, "contract-test-3");

        ResponseEntity<Map> resp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "maybe", "actor", "human", "reason", "unsure"),
                Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void missingOrBlankActorReturns400() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        String workflowId = engine.submit("demo", stages, "contract-test-6");

        ResponseEntity<Map> missingResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "approved", "reason", "ok"), // no "actor" key at all
                Map.class);
        assertThat(missingResp.getStatusCode().value()).isEqualTo(400);
        assertThat(missingResp.getBody()).containsKey("error");

        ResponseEntity<Map> blankResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "approved", "actor", "  ", "reason", "ok"),
                Map.class);
        assertThat(blankResp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void missingOrBlankReasonReturns400() {
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), ctx -> Map.of(), true, false, 0, 30, null, null, null));
        String workflowId = engine.submit("demo", stages, "contract-test-7");

        ResponseEntity<Map> missingResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "approved", "actor", "human"), // no "reason" key at all
                Map.class);
        assertThat(missingResp.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<Map> blankResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/risky"),
                Map.of("decision", "approved", "actor", "human", "reason", ""),
                Map.class);
        assertThat(blankResp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void stepInputsSnapshotOnlyContainsArtifactsAvailableWhenThatStageBegan() {
        // Three sequential stages, each producing one artifact the next depends on. An earlier
        // step's persisted inputs must never contain an artifact a LATER step produced.
        List<StageDefinition> stages = List.of(
                StageDefinition.of("one", List.of(), ctx -> {
                    ctx.addArtifact("from-one", "A");
                    return Map.of();
                }),
                StageDefinition.of("two", List.of("one"), ctx -> {
                    ctx.addArtifact("from-two", "B");
                    return Map.of();
                }),
                StageDefinition.of("three", List.of("two"), ctx -> {
                    ctx.addArtifact("from-three", "C");
                    return Map.of();
                }));
        String workflowId = engine.submit("demo", stages, "contract-test-8");

        ResponseEntity<Map> stateResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(stateResp.getBody().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) stateResp.getBody().get("steps");
        Map<String, Map<String, Object>> byName = new java.util.HashMap<>();
        for (Map<String, Object> s : steps) byName.put((String) s.get("name"), s);

        Map<String, Object> oneArtifacts = (Map<String, Object>)
                ((Map<String, Object>) byName.get("one").get("inputs")).get("contextArtifactsAtStageStart");
        Map<String, Object> twoArtifacts = (Map<String, Object>)
                ((Map<String, Object>) byName.get("two").get("inputs")).get("contextArtifactsAtStageStart");
        Map<String, Object> threeArtifacts = (Map<String, Object>)
                ((Map<String, Object>) byName.get("three").get("inputs")).get("contextArtifactsAtStageStart");

        // "one" began before anything ran: no artifacts at all yet.
        assertThat(oneArtifacts).doesNotContainKeys("from-one", "from-two", "from-three");
        // "two" began after "one" finished: sees "from-one", but not its own or "three"'s.
        assertThat(twoArtifacts).containsKey("from-one");
        assertThat(twoArtifacts).doesNotContainKeys("from-two", "from-three");
        // "three" began after "two" finished: sees both prior artifacts, but never its own.
        assertThat(threeArtifacts).containsKeys("from-one", "from-two");
        assertThat(threeArtifacts).doesNotContainKey("from-three");
    }

    @Test
    void approvalOnAStepWithNoPendingGateReturns404() {
        List<StageDefinition> stages = List.of(StageDefinition.of("plain", List.of(), ctx -> Map.of()));
        String workflowId = engine.submit("demo", stages, "contract-test-4");

        ResponseEntity<Map> resp = rest.postForEntity(
                url("/workflows/" + workflowId + "/approvals/plain"),
                Map.of("decision", "approved", "actor", "human", "reason", "n/a"),
                Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
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
        // Round-6 correction (Codex REQUEST AMENDMENTS item 3): openapi.yaml specifies 204 for a
        // valid approval, not the framework's implicit 200.
        assertThat(approveResp.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<Map> afterResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(afterResp.getBody().get("status")).isEqualTo("COMPLETED");
    }
}
