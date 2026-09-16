# TDD execution evidence

Date: 2026-09-15

This record captures observed red/green cycles from the final contract-hardening review. It does not
claim that Git history proves an observed-red run for every earlier behavior.

| Behavior | Red evidence | Minimal implementation | Green evidence |
|---|---|---|---|
| Reject URLs longer than 2,048 characters | The new unit and HTTP contract assertions initially failed because `UrlValidator` accepted the over-length value. | Added the 2,048-character guard and `url_too_long` reason. | Focused validation/contract tests passed; included in the final 241-test suite. |
| `GET /links/{code}` metadata contract | The new success/unknown-code tests initially received an absent route/default 404 shape. | Added the metadata mapping and normalized `ResponseStatusException` error response. | `LinkControllerContractTest` passed, including expired metadata without incrementing analytics. |
| `GET /readyz` degraded dependency state | `HealthControllerFailureTest` first ran with two non-green cases: invalid connection returned 200, and connection failure escaped as `SQLException`. | Return `503 {"status":"not_ready"}` when `DataSource#getConnection` fails or `Connection#isValid` is false. | `HealthControllerTest,HealthControllerFailureTest,LinkControllerContractTest`: 14 tests, zero failures/errors. |

The analytics unknown-code assertion was added as characterization/contract-completeness coverage;
it passed against existing behavior and is not presented as a red/green cycle.

Final verification:

```text
./mvnw --batch-mode verify
Tests run: 241, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The same submission tree passed `verify` after reconstruction in a separate temporary directory.
