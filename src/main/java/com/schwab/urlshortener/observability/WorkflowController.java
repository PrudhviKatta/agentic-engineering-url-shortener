package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.api.dto.ErrorResponse;
import com.schwab.urlshortener.observability.dto.*;
import com.schwab.urlshortener.orchestration.BaselineWorkflowGraph;
import com.schwab.urlshortener.orchestration.InvalidApprovalDecisionException;
import com.schwab.urlshortener.orchestration.NoPendingApprovalException;
import com.schwab.urlshortener.orchestration.UnreadableRequirementException;
import com.schwab.urlshortener.orchestration.WorkflowEngine;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final WorkflowEngine engine;

    // Corrected 2026-09-13: inject the shared @Component WorkflowEngine bean instead of
    // constructing a private one — a second, independently-constructed engine would have its
    // own empty registry, unable to see workflows submitted through ScenarioRunner's engine.
    public WorkflowController(WorkflowInstanceJpaRepository instanceRepository,
                               WorkflowStepJpaRepository stepRepository,
                               WorkflowEventJpaRepository eventRepository,
                               WorkflowEngine engine) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.engine = engine;
    }

    // Codex REQUEST AMENDMENTS item 1 (T026a): generic Workflow Run creation over HTTP.
    // openapi.yaml's own contract for this operation is explicit — "mandatory, full
    // implementation, no stub path... no 501/not-implemented escape hatch." Instantiates the SAME
    // baseline 12-stage graph ScenarioRunner's CLI harness uses (BaselineWorkflowGraph), so the two
    // creation paths cannot silently drift, per T026a's own risk/guardrail.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowCreatedResponse createWorkflow(@RequestBody CreateWorkflowRequest request) {
        if (request == null || request.requirementText() == null || request.requirementText().isBlank()) {
            throw new UnreadableRequirementException("requirementText must be present and non-blank");
        }
        if (request.feature() == null || request.feature().isBlank()) {
            throw new UnreadableRequirementException("feature must be present and non-blank");
        }
        String workflowId = engine.submit(request.feature(), BaselineWorkflowGraph.stages(), UUID.randomUUID().toString());
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        return new WorkflowCreatedResponse(instance.getId(), instance.getStatus(),
                instance.getCorrelationId(), instance.getPlanVersion());
    }

    @GetMapping("/{id}")
    public WorkflowStateResponse getWorkflow(@PathVariable("id") String id) {
        WorkflowInstanceEntity instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow_not_found"));

        // Corrected 2026-09-13: repository method is plan-version-scoped since Task 21
        // (findByWorkflowIdAndPlanVersionOrderBySequence), not the bare findByWorkflowIdOrderBySequence
        // this brief originally showed.
        List<WorkflowStateResponse.StepView> steps = stepRepository
                .findByWorkflowIdAndPlanVersionOrderBySequence(id, instance.getPlanVersion()).stream()
                .map(s -> {
                    // Round-6c correction: each step's inputs reflect ITS OWN persisted snapshot —
                    // the artifacts actually available when its batch began — not the workflow's
                    // current/final artifact map attached identically to every historical step.
                    // Null (omitted) for a step whose batch hasn't started yet.
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    inputs.put("dependsOn", s.getDependsOn());
                    if (s.getInputsSnapshot() != null) {
                        inputs.put("contextArtifactsAtStageStart", s.getInputsSnapshot());
                    }
                    return new WorkflowStateResponse.StepView(
                            s.getName(), s.getStatus(), s.getStartedAt(), s.getEndedAt(), s.getRetryCount(),
                            inputs, s.getResult());
                })
                .toList();

        List<WorkflowStateResponse.DecisionLineageEntry> decisionLineage = decisionLineage(instance);

        return new WorkflowStateResponse(instance.getId(), instance.getStatus(), instance.getCorrelationId(),
                instance.getPlanVersion(), decisionLineage, steps);
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowStateResponse.DecisionLineageEntry> decisionLineage(WorkflowInstanceEntity instance) {
        List<Map<String, Object>> decisions =
                (List<Map<String, Object>>) instance.getContextJson().getOrDefault("decisions", List.of());
        return decisions.stream()
                .map(d -> new WorkflowStateResponse.DecisionLineageEntry(
                        (String) d.get("actor"),
                        (String) d.get("decision"),
                        (String) d.get("rationale"),
                        d.get("timestamp") != null ? Instant.parse((String) d.get("timestamp")) : null))
                .toList();
    }

    @GetMapping("/{id}/audit")
    public List<AuditEventResponse> getAudit(@PathVariable("id") String id) {
        instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow_not_found"));
        return eventRepository.findByWorkflowIdOrderById(id).stream()
                .map(e -> new AuditEventResponse(e.getWorkflowId(), e.getActorType(), e.getActorId(), e.getAction(),
                        e.getTimestamp(), e.getAffectedArtifact(), e.getPayload(), e.getResult(), e.getReason()))
                .toList();
    }

    @PostMapping("/{id}/approvals/{step}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable("id") String id, @PathVariable("step") String step,
                         @RequestBody ApprovalRequest request) {
        engine.approve(id, step, request.decision(), request.actor(), request.reason());
    }

    @ExceptionHandler(UnreadableRequirementException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequirement(UnreadableRequirementException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(InvalidApprovalDecisionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDecision(InvalidApprovalDecisionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(NoPendingApprovalException.class)
    public ResponseEntity<ErrorResponse> handleNoPendingApproval(NoPendingApprovalException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ex.getMessage()));
    }

    // Round-6b correction (independent-review finding): getWorkflow/getAudit's unknown-workflow
    // 404 was a bare ResponseStatusException, which Spring renders as its own default
    // {timestamp,status,error,path} body — not openapi.yaml's ErrorResponse{error:{reason}} shape.
    // This handler normalizes every ResponseStatusException thrown by this controller to that shape.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ErrorResponse.of(ex.getReason()));
    }
}
