# Brownfield scenario

## Change and impact boundary

SCN-B hardens the existing SSRF control so IPv6 Unique Local Addresses in `fc00::/7` are rejected.
The change is isolated to address classification: no public API or schema changes, and link
validation still completes before persistence. The earlier expiration-update example is not used
because links are immutable under CLR-15.

## Seven-branch impact analysis and join

After the design approval, seven persisted siblings analyze components, interfaces, data flows,
tests, documentation, regression risk, and rollout/rollback. They produce these named artifacts:

- `impact_components_finding`
- `impact_interfaces_finding`
- `impact_data_flows_finding`
- `impact_tests_finding`
- `impact_documentation_finding`
- `impact_regression_risk_finding`
- `impact_rollout_rollback_finding`

The `change_impact_report` stage depends on all seven rows and validates all seven artifacts before
marking the report complete. Its exit approval blocks implementation. Persisted timestamps and the
scenario test prove every sibling ended before the joined report began.

## Change, regression, and rollback evidence

The security tests cover both ULA prefixes (`fc00::1` and `fd00::1`) while preserving acceptance of
a public IPv6 address. The main regression risk is an over-broad prefix check. Rollback is limited
to the address-classification change because neither data nor API migration is involved. Release
readiness refuses a brownfield-ready result if the rollout/rollback finding is missing.

## Executed evidence

The current CLI run completed as workflow `84411566-7ca6-4619-8ffe-6b44a7487b57`:

```json
{"action":"stage_succeeded:impacted_components","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:impacted_interfaces","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:impacted_data_flows","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:impacted_tests","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:impacted_documentation","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:regression_risk","actorType":"agent","result":"OK"}
{"action":"stage_succeeded:rollout_rollback","actorType":"agent","result":"OK"}
{"action":"approval_approved:change_impact_report","actorType":"human","result":"OK"}
{"status":"COMPLETED","workflowId":"84411566-7ca6-4619-8ffe-6b44a7487b57"}
```

Reproduce with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test \
  -Dtest=BrownfieldScenarioTest,UrlValidatorSsrfTest,UrlValidatorTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=brownfield
```
