package com.schwab.urlshortener.scenarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ScenarioRunner implements CommandLineRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final WorkflowEngine engine;

    public ScenarioRunner(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowEventJpaRepository eventRepository,
                           WorkflowEngine engine) {
        this.instanceRepository = instanceRepository;
        this.eventRepository = eventRepository;
        this.engine = engine;
    }

    @Override
    public void run(String... args) throws Exception {
        String scenario = extractScenarioArg(args);
        if (scenario == null) return; // normal server boot; not invoked in scenario mode
        runScenario(scenario, System.out);
    }

    static String extractScenarioArg(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--scenario=")) return arg.substring("--scenario=".length());
        }
        return null;
    }

    void runScenario(String scenario, PrintStream out) throws Exception {
        // Codex REQUEST AMENDMENTS item 1 (T026a): the same shared baseline 12-stage graph
        // POST /workflows now instantiates — per T026a's own risk/guardrail, both creation paths
        // must build from the identical factory or they silently drift. Replaces this runner's
        // prior trivial 2-stage smoke-test placeholder graph.
        List<StageDefinition> stages = BaselineWorkflowGraph.stages();
        String workflowId = engine.submit(scenario, stages, "cli-" + scenario);

        for (WorkflowEventEntity event : eventRepository.findByWorkflowIdOrderById(workflowId)) {
            Map<String, Object> line = Map.of(
                    "actorType", event.getActorType(), "action", event.getAction(),
                    "timestamp", event.getTimestamp().toString(), "result", event.getResult());
            out.println(MAPPER.writeValueAsString(line));
        }

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        out.println(MAPPER.writeValueAsString(Map.of("workflowId", workflowId, "status", instance.getStatus().name())));
    }
}
