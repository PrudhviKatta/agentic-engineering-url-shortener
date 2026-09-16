# Greenfield scenario

SCN-A starts with a measurable requirement: `REQ-023: accept 20 concurrent link creations with zero duplicate short codes`.

The production twelve-stage graph ingests and normalizes that input, records that it contains both a requirement identifier and a measurable criterion, and persists `human_clarification` as `SKIPPED` with the `ambiguity_gate` reason. Decomposition and design then run; the design exit gate requires an explicit approval before implementation proceeds.

Implementation records the URL-scheme and SSRF controls. Documentation runs from the approved design while testing and security run from implementation evidence. Release readiness joins those results, checks policy, security, testing, documentation, audit, and fallback evidence, and requires a second explicit approval. The final engineering summary runs only after that approval.

Executable evidence: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=GreenfieldScenarioTest`.
