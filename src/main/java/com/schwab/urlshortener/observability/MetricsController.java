package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;

    public MetricsController(WorkflowInstanceJpaRepository instanceRepository,
                             WorkflowStepJpaRepository stepRepository,
                             WorkflowEventJpaRepository eventRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/metrics")
    public ReliabilitySummary metrics() {
        return MetricsService.computeReliabilitySummary(instanceRepository, stepRepository, eventRepository, null);
    }
}
