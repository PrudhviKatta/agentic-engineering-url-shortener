# Three-scenario evidence review

This is a self-audit of the executable scenario implementations. It is not independent sign-off.
Every evidence pointer below names a checked-in path or a command that can be rerun.

| Scenario | Requirement input | Interpretation | Decomposition | Dependency graph | Orchestration path | State transitions | Human gates | Tests | Executed validation | Failure behavior | Audit evidence | Docs match execution | Terminal outcome | Disclosed limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SCN-A Greenfield | `REQ-023` plus a measurable 20-request criterion | Clear and testable; no clarification invented | `DecompositionStage` emits reviewable units | Baseline 12-node DAG in `BaselineWorkflowGraph` | ingest → normalize → ambiguity check → clarification skipped → decomposition → design → implementation → testing/security/documentation → release readiness → summary | `human_clarification` becomes `SKIPPED`; all executed steps reach success after gates | Design and release-readiness exit gates | `GreenfieldScenarioTest` | `./mvnw test -Dtest=GreenfieldScenarioTest` | Release path cannot pass either unapproved exit gate; mandatory policy failures safe-stop | `workflow_events` assertions cover skip and approvals | `docs/scenarios/greenfield.md` matches graph and assertions | `COMPLETED` | Requirement analysis is a documented demonstration heuristic, not general NLP |
| SCN-B Brownfield | Harden existing SSRF validation for IPv6 ULA `fc00::/7` | Security correction with no API/schema mutation | One code change plus regression and rollback plan | Baseline DAG extended by seven independent impact branches and a join gate in `BrownfieldWorkflowGraph` | approved design → seven impact siblings in one batch → joined report → approval → implementation → verification join → release | All seven intervals overlap; report starts only after every branch ends | Design, change-impact report, and release-readiness gates | `BrownfieldScenarioTest`, `UrlValidatorSsrfTest`, `UrlValidatorTest` | `./mvnw test -Dtest=BrownfieldScenarioTest,UrlValidatorSsrfTest,UrlValidatorTest` | Report rejection leaves implementation `PENDING` and workflow terminal; public IPv6 remains allowed | Audit and persisted step timestamps prove branch execution and gate decision | `docs/scenarios/brownfield.md` names the actual fix, branches, risks, and rollback | `COMPLETED` on approval; `FAILED` on demonstrated rejection | No distributed-worker timing claim; concurrency evidence is local-process execution |
| SCN-C Ambiguous | “Make the short-link workflow better.” | High-impact ambiguity because scope and measurable criterion are absent | Blocked until clarification; optional replan replaces it with `decomposition_v2` | Baseline graph, then a persisted plan-version-2 replacement subgraph | ingest → normalize → ambiguity → clarification wait → replan → approve → v2 decomposition → normal verification | Workflow enters `PENDING_APPROVAL`; old downstream rows become `INVALIDATED`; replacements run | Clarification, design, and release-readiness gates | `AmbiguousScenarioTest` | `./mvnw test -Dtest=AmbiguousScenarioTest` | No downstream decomposition occurs before clarification; replan cannot bypass governance | Assertions prove `replanned` precedes clarification approval and reason flows downstream | `docs/scenarios/ambiguous.md` matches persisted plan/version behavior | `COMPLETED` after clarification and approvals | Replan mutations are transactionally consistent, but no distributed coordinator is claimed |

## Material-difference check

The scenarios are not copies of one graph with renamed input. SCN-A proves conditional skipping,
SCN-B adds a seven-way concurrent brownfield analysis and a rejection path, and SCN-C proves real
suspension plus invalidation/regeneration under a new plan version. Their tests assert different
state transitions, gates, failure behavior, and audit ordering.

## Review result

All fourteen evidence categories have concrete pointers for all three scenarios. The self-audit
found no missing mandatory scenario artifact. Independent assessment remains a separate release
step in `docs/assessment/final_independent_assessment.md`.
