# Ambiguous-requirement scenario

## Input and ambiguity decision

SCN-C begins with `add rate limiting`. It supplies no threshold, caller scope, or measurable
acceptance criterion. `ambiguity_detection` therefore records high-impact ambiguity and the
always-present `human_clarification` step waits for approval. The original decomposition path
cannot execute while that gate is unresolved.

The recorded clarification is `rate limit set to 100 requests per minute per IP`. That rationale is
persisted in the approval lineage and as `human_clarificationResolution`; downstream decomposition
must include the same resolved scope.

## Replan and replacement graph

Before approval, `replan()` marks the durable replan intent, invalidates the unstarted
`decomposition` row and every reachable downstream row, increments `planVersion`, and persists
`decomposition_v2` plus its regenerated downstream graph. Unaffected completed requirement stages
retain their original lineage. The approval is then evaluated against the current plan, the
replacement decomposition records `scopeVersion: 2`, and execution continues through the normal
design and release gates.

## Executed evidence

The current CLI run completed as workflow `2835b94d-d34c-463d-8bd2-7c2591c19109`. Event ordering
shows the replan before clarification approval and the replacement stage after approval:

```json
{"action":"replanned","actorType":"human","result":"OK"}
{"action":"approval_approved:human_clarification","actorType":"human","result":"OK"}
{"action":"stage_succeeded:human_clarification","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:decomposition_v2","actorType":"agent","result":"OK"}
{"status":"COMPLETED","workflowId":"2835b94d-d34c-463d-8bd2-7c2591c19109"}
```

The scenario test also checks that the old downstream rows are `INVALIDATED`, the new rows carry
plan version 2, and the clarification rationale reaches decomposition.

Reproduce with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=AmbiguousScenarioTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=ambiguous
```
