# Greenfield scenario

## Input and selected path

SCN-A starts with the measurable requirement `REQ-023: accept 20 concurrent link creations with
zero duplicate short codes`. The twelve-stage baseline graph persists the requirement, normalized
text, dependencies, handler keys, activation key, gate configuration, retry limit, timeout, owner,
status, input snapshot, result, and plan version for inspection.

`ambiguity_detection` records a clear/measurable result. The conditional
`human_clarification` row is therefore persisted as `SKIPPED` with the `ambiguity_gate` reason;
decomposition begins without inventing a clarification.

## Decisions, artifacts, and gates

The run produces requirement-ingestion, normalization, ambiguity, decomposition, design,
implementation, testing, security, documentation, release-readiness, and final-summary results.
Named context artifacts include `normalizedRequirement`, `design`, `testResults`,
`securityResults`, `documentationEvidence`, and `releaseReadiness`.

The design exit gate blocks implementation until the self-asserted demonstration reviewer records
an explicit approval and rationale. Testing, security, and documentation then converge at release
readiness. Its exit gate also requires an explicit approval; silence never releases either gate.

## Executed evidence

The current CLI run used isolated file-backed H2 storage and completed as workflow
`2f9c8a4d-dfe0-4432-9d30-37ec958d3154`:

```json
{"action":"stage_skipped:human_clarification","actorType":"orchestrator","result":"OK"}
{"action":"approval_approved:design","actorType":"human","result":"OK"}
{"action":"approval_approved:release_readiness","actorType":"human","result":"OK"}
{"status":"COMPLETED","workflowId":"2f9c8a4d-dfe0-4432-9d30-37ec958d3154"}
```

Reproduce the behavior and persisted assertions with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=GreenfieldScenarioTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=greenfield
```
