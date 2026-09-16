# Ambiguous-requirement scenario

SCN-C begins with “Make the short-link workflow better.” It has neither a requirement identifier nor a measurable acceptance criterion, so ambiguity detection records a high-impact clarification need and the always-present `human_clarification` step enters `WAITING_APPROVAL`. Decomposition has not started at that point.

The recorded decision supplies a concrete scope: apply the change only to `REQ-007` and require a 25-character validation criterion. That approval reason is persisted as `human_clarificationResolution`; the downstream decomposition result must contain the same text.

A second executable path calls `replan()` before approval. It invalidates the unstarted `decomposition` and all reachable downstream rows, increments the plan version, and inserts `decomposition_v2` plus its regenerated downstream graph. The audit order proves `replanned` precedes `approval_approved:human_clarification`. After approval, the replacement runs with `scopeVersion: 2`, both normal exit gates are approved, and the workflow completes.

Executable evidence: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=AmbiguousScenarioTest`.
