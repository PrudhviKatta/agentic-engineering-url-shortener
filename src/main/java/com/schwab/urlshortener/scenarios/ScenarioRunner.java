package com.schwab.urlshortener.scenarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.observability.MetricsService;
import com.schwab.urlshortener.orchestration.BaselineWorkflowGraph;
import com.schwab.urlshortener.orchestration.AmbiguousWorkflowGraph;
import com.schwab.urlshortener.orchestration.BrownfieldWorkflowGraph;
import com.schwab.urlshortener.orchestration.StageDefinition;
import com.schwab.urlshortener.orchestration.WorkflowEngine;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ScenarioRunner implements CommandLineRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final WorkflowEngine engine;

    public ScenarioRunner(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           WorkflowEngine engine) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.engine = engine;
    }

    @Override
    public void run(String... args) throws Exception {
        if (isMetricsReport(args)) {
            printMetricsReport(System.out);
            return;
        }
        String scenario = extractScenarioArg(args);
        if (scenario == null) return; // normal server boot; not invoked in scenario mode
        runScenario(scenario, System.out);
    }

    static boolean isMetricsReport(String[] args) {
        for (String arg : args) {
            if ("--metrics-report".equals(arg)) return true;
        }
        return false;
    }

    public void printMetricsReport(PrintStream out) throws Exception {
        var summary = MetricsService.computeReliabilitySummary(
                instanceRepository, stepRepository, eventRepository, null);
        out.println(MAPPER.writeValueAsString(summary));
    }

    static String extractScenarioArg(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--scenario=")) return arg.substring("--scenario=".length());
        }
        return null;
    }

    void runScenario(String scenario, PrintStream out) throws Exception {
        ScenarioDefinition definition = definitionFor(scenario);
        String workflowId = engine.submit(scenario, definition.stages(), "cli-" + scenario,
                definition.requirement());

        if ("ambiguous".equals(scenario)) {
            engine.replan(workflowId, "decomposition", AmbiguousWorkflowGraph.replannedStages(),
                    "scenario-reviewer", "narrow the ambiguous request to one versioned delivery scope");
        }
        for (String gate : definition.approvalOrder()) {
            engine.approve(workflowId, gate, "approved", "scenario-reviewer",
                    "demonstration approval for " + gate + " after inspecting persisted evidence");
        }

        for (WorkflowEventEntity event : eventRepository.findByWorkflowIdOrderById(workflowId)) {
            Map<String, Object> line = Map.of(
                    "actorType", event.getActorType(), "action", event.getAction(),
                    "timestamp", event.getTimestamp().toString(), "result", event.getResult());
            out.println(MAPPER.writeValueAsString(line));
        }

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        out.println(MAPPER.writeValueAsString(Map.of("workflowId", workflowId, "status", instance.getStatus().name())));
    }

    private static ScenarioDefinition definitionFor(String scenario) {
        return switch (scenario) {
            case "greenfield" -> new ScenarioDefinition(BaselineWorkflowGraph.stages(),
                    "REQ-023: accept 20 concurrent link creations with zero duplicate short codes",
                    List.of("design", "release_readiness"));
            case "brownfield" -> new ScenarioDefinition(BrownfieldWorkflowGraph.stages(),
                    "REQ-021: reject 100 percent of IPv6 ULA fc00::/7 targets without rejecting public IPv6",
                    List.of("design", "change_impact_report", "release_readiness"));
            case "ambiguous" -> new ScenarioDefinition(BaselineWorkflowGraph.stages(),
                    "Make the short-link workflow better",
                    List.of("human_clarification", "design", "release_readiness"));
            default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
        };
    }

    private record ScenarioDefinition(List<StageDefinition> stages, String requirement,
                                      List<String> approvalOrder) { }
}
