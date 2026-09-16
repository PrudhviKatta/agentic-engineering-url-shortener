# Contributing

## Development baseline

- Use Java 21 and the checked-in Maven wrapper.
- Keep domain, persistence, orchestration, policy, reliability, and observability boundaries explicit.
- Add or update tests before changing behavior.
- Preserve unrelated working-tree changes and never commit generated H2 database files.

## Change workflow

1. Map the change to a requirement and, when material, an ADR.
2. Write a failing test for the intended behavior or regression.
3. Implement the smallest coherent change.
4. Run the focused tests, then `./mvnw test`.
5. Update the OpenAPI contract, data model, scenario evidence, and traceability matrix when affected.
6. Record security, rollback, migration, and compatibility impact explicitly.

Approval and release decisions must retain a real actor and rationale. Never fabricate an approval or
rewrite test output as evidence. Generated reports must quote results from commands that were actually
run against the revision being reviewed.

## Commit quality

Use focused commits with an imperative summary. Do not commit `target/`, `data/*.mv.db`, local IDE
state, credentials, or temporary evidence. A release candidate must pass the clean-clone procedure in
`docs/assessment/reviewer_navigation_guide.md` and preserve a readable history.
