package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.observability.dto.*;
import com.schwab.urlshortener.orchestration.WorkflowEngine;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/{id}")
    public WorkflowStateResponse getWorkflow(@PathVariable("id") String id) {
        WorkflowInstanceEntity instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow_not_found"));
        // Corrected 2026-09-13: repository method is plan-version-scoped since Task 21
        // (findByWorkflowIdAndPlanVersionOrderBySequence), not the bare findByWorkflowIdOrderBySequence
        // this brief originally showed.
        List<WorkflowStateResponse.StepView> steps = stepRepository
                .findByWorkflowIdAndPlanVersionOrderBySequence(id, instance.getPlanVersion()).stream()
                .map(s -> new WorkflowStateResponse.StepView(
                        s.getName(), s.getStatus(), s.getStartedAt(), s.getEndedAt(), s.getRetryCount()))
                .toList();
        return new WorkflowStateResponse(instance.getId(), instance.getStatus(), steps);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEventResponse> getAudit(@PathVariable("id") String id) {
        return eventRepository.findByWorkflowIdOrderById(id).stream()
                .map(e -> new AuditEventResponse(e.getActorType(), e.getAction(), e.getTimestamp(), e.getResult(), e.getReason()))
                .toList();
    }

    @PostMapping("/{id}/approvals/{step}")
    public void approve(@PathVariable("id") String id, @PathVariable("step") String step,
                         @RequestBody ApprovalRequest request) {
        engine.approve(id, step, request.decision(), request.actor(), request.reason());
    }
}
