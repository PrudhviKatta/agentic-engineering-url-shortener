# Brownfield scenario

## Change impact summary

SCN-B hardens the existing SSRF control so IPv6 Unique Local Addresses in `fc00::/7` are rejected. This is an implementation correction, not an API or schema change. The earlier expiration-update demonstration is intentionally not used because links are immutable under CLR-15.

## Dependency map and gate

After the approved design, seven independent branches analyze components, interfaces, data flows, tests, documentation, regression risk, and rollout/rollback. They run in one concurrent batch. `change_impact_report` joins all seven outputs and has a mandatory exit approval; implementation remains pending until that report is approved.

## Test-first change plan

The security regression suite checks both ULA boundaries (`fc00::1` and `fd00::1`) and retains a positive public-IPv6 case. Run `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=BrownfieldScenarioTest,UrlValidatorSsrfTest,UrlValidatorTest`.

## Regression and rollback

The principal risk is an over-broad prefix check that rejects public IPv6 addresses. The rollback is to revert only the address-classification change; no database or API migration is involved. The release-readiness stage refuses a brownfield-ready result when the rollout/rollback finding is absent.

## Replanning and approval

No replan is needed for the demonstrated fix. Rejection of the joined impact report terminates that workflow version and proves implementation cannot begin without the required human decision.
