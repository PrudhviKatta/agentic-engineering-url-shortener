# Agentic engineering URL shortener

A Java 21/Spring Boot URL shortener paired with a durable engineering-workflow engine. The project
demonstrates persisted DAG execution, explicit approvals, policy gates, bounded recovery,
replanning, audit evidence, and three materially different delivery scenarios.

## Quickstart

```bash
./mvnw spring-boot:run
```

Java 21 is required. The service binds to `127.0.0.1:8080`. Useful endpoints include `POST /links`,
`GET /links/{code}`, `GET /r/{code}`, `GET /links/{code}/analytics`, `POST /workflows`,
`GET /workflows/{id}`, and `GET /metrics`.

Create and follow a short link:

```bash
curl -i -X POST 'http://127.0.0.1:8080/links' \
  -H 'Content-Type: application/json' \
  --data-raw '{"longUrl":"https://example.com"}'
curl -i 'http://127.0.0.1:8080/r/{code}'
```

Run all tests:

```bash
./mvnw verify
```

Run a demonstration scenario or the metrics report:

```bash
./mvnw -Dspring-boot.run.arguments=--scenario=greenfield spring-boot:run
./mvnw -Dspring-boot.run.arguments=--scenario=brownfield spring-boot:run
./mvnw -Dspring-boot.run.arguments=--scenario=ambiguous spring-boot:run
./mvnw -Dspring-boot.run.arguments=--metrics-report spring-boot:run
```

Start with the [reviewer navigation guide](docs/assessment/reviewer_navigation_guide.md) for the
25-point evidence walkthrough. Architecture and full requirement mapping are in
[architecture overview](docs/architecture/overview.md) and
[traceability matrix](docs/traceability/matrix.md). The release decision and candid scoring are in
the [final engineering summary](docs/assessment/final_engineering_summary.md) and
[final independent assessment](docs/assessment/final_independent_assessment.md).

## Scope

This is a loopback-only assessment implementation, not a production internet service. Authentication,
rate limiting, production migrations, distributed execution, and production telemetry remain explicit
limitations; see [SECURITY.md](SECURITY.md) and the final assessment.
