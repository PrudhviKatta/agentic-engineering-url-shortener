package com.schwab.urlshortener.orchestration;

import java.util.*;

public class DependencyGraph {

    private final Map<String, StageDefinition> stages;

    public DependencyGraph(List<StageDefinition> stageList) {
        this.stages = new LinkedHashMap<>();
        for (StageDefinition stage : stageList) {
            stages.put(stage.name(), stage);
        }
    }

    public void validate() {
        for (StageDefinition stage : stages.values()) {
            for (String dep : stage.dependsOn()) {
                if (!stages.containsKey(dep)) {
                    throw new UnknownDependencyException(stage.name() + " depends on unknown stage " + dep);
                }
            }
        }
        topologicalOrder(); // throws CycleException if cyclic
    }

    private List<String> topologicalOrder() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> temp = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();
        for (String name : stages.keySet()) {
            visit(name, visited, temp, order);
        }
        return order;
    }

    private void visit(String name, Set<String> visited, Set<String> temp, List<String> order) {
        if (visited.contains(name)) return;
        if (temp.contains(name)) {
            throw new CycleException("cycle detected at " + name);
        }
        temp.add(name);
        for (String dep : stages.get(name).dependsOn()) {
            visit(dep, visited, temp, order);
        }
        temp.remove(name);
        visited.add(name);
        order.add(name);
    }

    public List<List<StageDefinition>> topologicalBatches() {
        Map<String, StageDefinition> remaining = new LinkedHashMap<>(stages);
        Set<String> done = new HashSet<>();
        List<List<StageDefinition>> batches = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<StageDefinition> ready = remaining.values().stream()
                    .filter(s -> done.containsAll(s.dependsOn()))
                    .toList();
            if (ready.isEmpty()) {
                throw new CycleException("no ready stages but stages remain");
            }
            batches.add(ready);
            for (StageDefinition s : ready) {
                done.add(s.name());
                remaining.remove(s.name());
            }
        }
        return batches;
    }
}
