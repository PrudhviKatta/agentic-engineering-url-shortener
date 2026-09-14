package com.schwab.urlshortener.orchestration;

import java.util.List;
import java.util.Map;

// Codex REQUEST AMENDMENTS item 1 (T026a): the shared baseline 12-node stage graph
// (plan.md Section 3.2, corrected shape per tasks.md T039's authoritative node list —
// requirement_ingestion, requirement_normalization, ambiguity_detection, human_clarification,
// decomposition, design, implementation, testing, security, documentation, release_readiness,
// final_engineering_summary). T026a's own prerequisites are T020/T021 only, not T039/T020b
// (StageCatalog) — this factory instantiates the GRAPH SHAPE (names, dependencies, gates) with
// placeholder no-op handlers, proving the structure is instantiable and persists correctly. Task
// 39 is what replaces these placeholder handlers with the real twelve stage classes.
//
// Per T026a's own risk/guardrail ("do not special-case this endpoint's instantiation logic
// separately from ScenarioRunner's") — this is the ONE factory both WorkflowController's
// POST /workflows and ScenarioRunner's CLI harness use, so the two creation paths cannot drift.
//
// Disclosed simplification: human_clarification is modeled here as an UNCONDITIONAL entry-approval
// gate (entryApproval=true, always halts) rather than the conditionally-activated
// (activationConditionKey="ambiguity_gate") node Task 39/tasks.md T041 describe — StageCatalog's
// conditional-activation mechanism is not wired into WorkflowEngine yet (T020b remains PARTIAL,
// deferred to Task 50). Every workflow created through this factory will halt at
// human_clarification's approval gate; Task 39 replaces this with the real conditional design.
public final class BaselineWorkflowGraph {

    private BaselineWorkflowGraph() { }

    public static List<StageDefinition> stages() {
        StageHandler noOp = ctx -> Map.of();
        return List.of(
                StageDefinition.of("requirement_ingestion", List.of(), noOp),
                StageDefinition.of("requirement_normalization", List.of("requirement_ingestion"), noOp),
                StageDefinition.of("ambiguity_detection", List.of("requirement_normalization"), noOp),
                new StageDefinition("human_clarification", List.of("ambiguity_detection"), noOp,
                        true, false, 0, 30, null, null, null),
                StageDefinition.of("decomposition", List.of("human_clarification"), noOp),
                new StageDefinition("design", List.of("decomposition"), noOp,
                        false, true, 0, 30, null, null, null),
                StageDefinition.of("implementation", List.of("design"), noOp),
                new StageDefinition("testing", List.of("implementation"), noOp,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of("design"), noOp,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("security", List.of("implementation"), noOp,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("release_readiness", List.of("testing", "documentation", "security"), noOp,
                        false, true, 0, 30, null, null, null),
                StageDefinition.of("final_engineering_summary", List.of("release_readiness"), noOp)
        );
    }
}
