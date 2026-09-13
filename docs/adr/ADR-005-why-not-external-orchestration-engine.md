# ADR-005: Why Not an External Orchestration Engine (Temporal/Camunda/Activiti)

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D5 already recorded as "Proposed for ADR at Task 7." Reviewed and accepted by the
human candidate (Prudhvidhar Reddy Katta) on 2026-09-13.

## Context

ADR-004 selected a custom in-process `WorkflowEngine` to execute the `spec.md` Section 3.1 stage
graph. Before settling on that answer, the alternative of building on an existing, production-grade
workflow-orchestration product — Temporal, Camunda, or Activiti — needed its own explicit
evaluation, both because constitution Principle II's requirements (explicit dependency graph,
parallel paths, synchronization, branching, dynamic replanning, interruption/resumption) are
exactly the class of problem these products exist to solve, and because reflexively rejecting
mature, purpose-built infrastructure in favor of a custom build deserves its own recorded rationale,
not a passing mention inside ADR-004. This ADR is that rationale, split into its own document
because the reasoning (infra/ops burden vs. a local prototype's actual needs) is substantial enough
to warrant independent review and because rejecting a category of tooling is itself a material
architectural decision under constitution Principle VII.

## Decision Drivers

- Operational footprint versus a local, 2–3 day, reviewer-run prototype: does exercising this
  requirement actually need a separate server process and its own persistence?
- Whether the product's own runtime would itself need to be independently audited to satisfy
  constitution Principle IX (audit-grade evidence), on top of this project's own code — i.e.,
  whether adopting the product *increases* or *decreases* the total auditable surface area.
- Whether the assignment's actual scope (a demonstration prototype, not a production deployment)
  justifies the setup/learning-curve cost of a new piece of infrastructure within the timebox.
- Whether the custom engine (ADR-004) can already satisfy every named orchestration characteristic
  without the product — if it can, adopting the product would add cost without closing a
  capability gap.

## Options Considered

### Option A: Temporal
- **Approach:** run workflow definitions as Temporal Workflows/Activities against a Temporal
  server (self-hosted or Temporal Cloud), with durable execution and crash-consistent replay.
- **Advantages:** production-grade durability guarantees (crash-consistent replay, built-in retry/
  timeout primitives, a mature UI for inspecting workflow history).
- **Disadvantages:** requires running a separate Temporal server (plus its own datastore) locally —
  infrastructure a reviewer must also stand up to run this prototype, directly working against the
  "runs locally, no external infrastructure" constraint this project applies consistently elsewhere
  (ADR-002's rejection of Postgres/Testcontainers for the same reason). Temporal's own runtime
  becomes a black box a reviewer would need to trust rather than read, working against Principle
  IX's audit-grade-evidence goal.
- **Risks:** learning curve and setup time within a 2–3 day timebox could consume time better spent
  on the actual domain/orchestration logic this assessment evaluates.
- **Implementation impact:** highest of the three products evaluated — a full re-architecture
  around Temporal's workflow/activity model, replacing `DependencyGraph`/`WorkflowEngine` entirely.
- **Assessment implications:** would demonstrate familiarity with a specific product rather than
  the ability to design an orchestration engine from first principles, which is closer to what this
  assessment is actually evaluating (constitution Principle II names the *characteristics*
  required, not a specific product).

### Option B: Camunda
- **Approach:** model the stage graph as a BPMN process definition executed by an embedded or
  standalone Camunda engine.
- **Advantages:** mature BPMN tooling, visual process diagrams, built-in human-task (approval)
  support.
- **Disadvantages:** even the embedded mode carries meaningful setup/configuration overhead (BPMN
  XML authoring, process engine configuration) relative to the actual complexity of this project's
  12-stage graph; BPMN's human-task model would need to be mapped onto this project's specific
  `entryApproval`/`exitApproval` distinction (ADR-004), which is not a native BPMN concept and would
  require custom extension either way.
- **Risks:** BPMN authoring/tooling unfamiliarity risk within the timebox; a reviewer unfamiliar
  with BPMN would need to learn a modeling notation to audit the workflow, rather than read Java
  source directly.
- **Implementation impact:** substantial — the stage graph would need to be re-expressed as BPMN,
  and this project's specific gate/replan/compensation semantics bolted on as custom extensions.
- **Assessment implications:** similar to Temporal — demonstrates product familiarity over
  from-first-principles engine design.

### Option C: Activiti
- **Approach:** similar to Camunda (Activiti is a related BPMN engine lineage).
- **Advantages/Disadvantages/Risks:** materially the same profile as Option B — BPMN-based,
  requiring a modeling notation and engine configuration disproportionate to this project's actual
  graph complexity.
- **Implementation impact/Assessment implications:** same reasoning as Option B; not separately
  re-evaluated in depth given the shared BPMN-engine profile.

### Option D: Custom in-process `WorkflowEngine` (selected — see ADR-004)
- **Approach:** documented fully in ADR-004; included here only for contrast.
- **Advantages:** zero additional infrastructure; every transition is Java source a reviewer reads
  directly, with no product runtime to independently audit; already demonstrably satisfies every
  named orchestration characteristic (ADR-004's Options analysis).
- **Disadvantages:** forgoes production-grade durability guarantees (e.g., Temporal's
  crash-consistent replay across process crashes mid-workflow) that a real production deployment of
  this system would likely want.
- **Risks:** the engine's own correctness is entirely this project's burden (see ADR-004's Risks).
- **Implementation impact:** already accounted for under ADR-004.
- **Assessment implications:** demonstrates engine design ability directly, which is closer to what
  a from-scratch agentic-orchestration assessment is testing for.

## Decision

**Rejected.** No external orchestration product (Temporal, Camunda, or Activiti) is adopted. The
custom `WorkflowEngine` (ADR-004) is used instead.

## Rationale

Every option in this category (A, B, C) shares the same disqualifying profile relative to this
project's actual constraints: each requires either a separate server process with its own
persistence (Temporal) or a modeling-notation/engine-configuration layer (Camunda/Activiti) that is
disproportionate to a local, 2–3 day, reviewer-run prototype's actual scale — a 12-stage graph that
the custom engine (ADR-004) already demonstrably executes correctly, per that ADR's own test suite.
Each option would also *increase* the total auditable surface a reviewer must trust, not decrease
it: a third-party runtime's internal state model is not something a reviewer can verify by reading
this repository's source the way `WorkflowEngine`/`DependencyGraph` can be. This directly conflicts
with constitution Principle IX's audit-grade-evidence goal, which this project treats as a design
constraint, not an afterthought. The custom engine is demonstrably auditable end-to-end in H2 and
in Java source; none of the three rejected products would be to the same degree without a reviewer
separately learning and trusting that product's own runtime.

## Consequences

**Positive:**
- Zero additional infrastructure to install, configure, or operate — the entire system runs from
  `./mvnw spring-boot:run` plus the JDK, matching the "no external infrastructure" constraint
  applied consistently elsewhere in this project (ADR-002).
- The full orchestration mechanism remains auditable as plain Java source, with no vendor black box
  a reviewer must separately trust.

**Negative:**
- Forgoes production-grade durability guarantees (e.g., Temporal's crash-consistent replay) that a
  real production deployment of this system would likely want. This is explicitly named as a
  production-scale gap, not a prototype defect — `plan.md`'s own "production-grade discipline vs.
  production-scale infrastructure" distinction (Section 1) is precisely the framing that justifies
  this trade-off.
- If this project were ever taken past prototype scale, the custom engine's resumption/replay
  guarantees would need independent hardening (e.g., against a mid-batch process crash) that a
  product like Temporal provides out of the box.

**Operational:**
- No separate orchestration server, message broker, or product-specific datastore to run, monitor,
  or patch.

**Testing:**
- N/A directly (this is a rejection, not a build) — validated indirectly by the same Phase 4 test
  suite (ADR-004's Validation section) proving the custom engine satisfies every required
  orchestration characteristic without the rejected infrastructure.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13 — see
  `docs/governance/human-gates-log.md`. Rejecting a category of infrastructure is itself a material
  decision under Principle VII and is recorded here rather than left as an implicit default.
  **Corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): this bullet
  previously read "is proposed pending... accept/reject at Task 7" after Task 7 had already run and
  this ADR's own Status header above had already moved to Accepted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| A future, real production deployment of this system would likely want Temporal-grade durability guarantees this design forgoes | Explicitly documented here and in `plan.md` Section 1 as a production-scale gap, not a prototype defect; revisiting this ADR is the recorded path if that need materializes |
| A reviewer might expect a "real" orchestration product for a system explicitly modeling SDLC orchestration | This ADR and ADR-004 together make the rejection rationale explicit and reviewable, rather than leaving the choice unexplained |

## Reversibility

**Low if ever revisited post-prototype.** Adopting an external product later would be a full
orchestration-layer rewrite — replacing `DependencyGraph`/`WorkflowEngine` and every stage handler's
integration point with that product's own workflow/activity model — not a configuration change or
an additive integration. This asymmetry (cheap to stay with the custom engine, expensive to switch
later) is disclosed here, not hidden.

## Traceability

- Requirements: REQ-011 (workflow creation), REQ-012 (workflow inspection), REQ-017 (workflow
  resumption) — the requirements this rejection is weighed against, since these are exactly the
  characteristics an external product would also need to satisfy.
- Specification sections: `spec.md` Section 3 (orchestration characteristics), constitution
  Principle II (explicit orchestration requirement) and Principle IX (audit-grade evidence).
- Plan sections: `plan.md` Tech Stack header ("No external orchestration infra
  (Temporal/Camunda/Activiti) — see ADR-005"), Section 12 (Technology Decisions, ADR-005 row),
  `research.md` D5.
- Related ADR: ADR-004 (Orchestration Model — the accepted alternative this ADR's rejection
  supports).
- Expected task identifiers: none directly (a rejection, not a build); validated by the same task
  set as ADR-004 (Tasks 20–41).

## Validation

N/A for direct validation (a rejection, not a build). Validated indirectly: the same Phase 4/6/7
test suite that validates ADR-004's custom `WorkflowEngine` (`DependencyGraphTest`,
`WorkflowEngineExecutionTest`, `WorkflowEngineApprovalTest`, `WorkflowEngineReliabilityIntegration
Test`, `WorkflowEngineSafeStopTest`, `WorkflowEngineReplanningTest`) is the evidence that the
rejected alternative was not, in fact, necessary to satisfy any named requirement.
