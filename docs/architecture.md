# URL Shortener Architecture

## Context

The system is a local-first modular monolith:

```text
Browser -> React UI -> Spring Boot API -> SQLite
                               |
                               +-> Structured logs/health
                               +-> bounded analytics writer
                               +-> orchestration control-plane API
```

The API is independently usable. No external service is required for local operation.

This architecture supports a production-ready local prototype. It
is local/trusted-demo only, single-instance, and bounded by the capacity targets
in `requirements.md`. Public hosting, multi-instance operation, and production
operations are intentionally outside the prototype boundary.

## Components

- **React UI**: accessible URL submission, client validation, result display, and clipboard feedback.
- **API layer**: Spring MVC controllers, DTOs, request validation, error mapping, and redirect handling.
- **Service/domain layer**: link creation, URL policy, secure code generation, and lookup use cases.
- **Repository layer**: Spring JDBC operations against SQLite; SQL is isolated here.
- **Configuration/observability**: environment settings, CORS, request IDs, structured logs, and health integration.
- **SQLite**: durable source of truth for a single application instance.
- **Analytics module**: redirect event publishing and time-window aggregate reads.
- **Orchestration module**: durable run/task coordination, approvals, audit, and
  terminal metrics for locally supplied workers.
- **Data model contract**: [`data-model.md`](data-model.md) defines the MVP
  `links` table and the V2 local orchestration/analytics schema additions.

## API boundary

- `POST /api/links`: create a link.
- `GET /{code}`: redirect.
- `GET /api/analytics/{code}`: local click aggregates.
- `/api/orchestration/runs...`: local orchestration run/task coordination and
  evidence operations.
- `GET /actuator/health`: health.
- Reserve `/api`, `/actuator`, `/assets`, and frontend routes from generated codes.

The API contract is owned by T04. T04 must publish the OpenAPI definition,
request/response schemas, error envelope, validation limits, redirect headers,
and configured-origin behavior before implementation tasks consume the
contract. Controllers must not expose framework exception details directly.
The published contract is [`openapi.yaml`](openapi.yaml).

## Data flow

Creation validates input, generates a code, persists the mapping, and returns the persisted result. Redirect lookup queries by the unique indexed code and returns a 302 response. After the response data is prepared, the redirect path offers a code/time event to the bounded asynchronous analytics publisher. Destination URLs are never fetched by the service.

Analytics persistence is intentionally best-effort: queue-full and writer
failures are isolated from redirect handling, and shutdown may drop queued events
after the bounded graceful-drain period. Aggregates are hourly UTC buckets over a
validated window of at most 366 days. Events contain only the short code and UTC
time; there is no client identity, IP, user-agent, destination, retention job, or
analytics UI.

### Local orchestration boundary

The repository contains a Spring Boot orchestration control plane. It durably
stores runs, tasks, dependencies, attempts, approvals, graph versions, audit
events, and terminal per-run metrics in SQLite. It validates dependency and
fallback graphs, applies entry/task/exit gates, supports worker claims and
results, bounded retry classification, fallback activation, cancellation,
rollback evidence, policy guardrails, and graph-versioned replanning.

The service records state and evidence; it does not execute task actions. A
caller polls ready tasks, claims them, performs the work, and submits a result.
There is no scheduler, queue, worker process, lease/heartbeat, timeout,
automatic recovery, fairness policy, or external-agent runtime client. The
external Orchestration Agent used for this development session is therefore a
separate runtime concern, documented in
[`orchestration-evidence.md`](orchestration-evidence.md).

## Cross-cutting architecture constraints

### Trust and origin handling

- The configured public origin is the only source for generated short URLs.
- Host, forwarded-host, forwarded-proto, and similar request headers are never
  used to construct public URLs unless a later approved proxy-trust policy
  explicitly defines and validates them.
- CORS allows only the configured local frontend origin; it is not a wildcard.
- The service never fetches, resolves, previews, or proxies destination URLs.

### Validation and error handling

The API validates HTTP/HTTPS schemes, malformed URLs, embedded credentials,
control characters, URL length, request-body size, and JSON content type at the
boundary. The request-body guard reads at most one byte beyond the 4,096-byte
limit, including for chunked transfer encoding. URL policy is shared with the
domain layer so UI validation is only a usability aid and never the security
authority. A centralized error mapper
returns the T04 error envelope with a request ID and UTC timestamp while
redacting destination URLs, SQL details, secrets, and stack traces from
responses and logs.

### Persistence and migrations

Flyway versioned migrations are the sole schema-change mechanism. Startup must
fail clearly when migrations cannot be applied or validated. Repository code is
the only layer allowed to issue SQL, and the database uniqueness constraint is
the authority for code collisions. Writes must be durable before a successful
create response. SQLite is operated as one database owned by one backend
instance. Backup, restore, integrity checking, and corruption recovery are
future public-service requirements, not prototype requirements.

### Concurrency, retries, and observability

Code generation uses a cryptographically secure random Base62 generator with a
bounded retry count. Concurrent creates and retry exhaustion must have tests;
retry loops must not become unbounded or leak sensitive data. Request IDs,
structured access/failure logs, timing, and database/migration health are
cross-cutting responsibilities, with controlled responses for database
failures.

`V1__create_links.sql` owns link data and `V2__orchestration_and_analytics.sql`
owns the local orchestration and click-event tables.

The analytics writer is also bounded and asynchronous; it is allowed to lose
events under queue saturation, writer failure, or forced shutdown so it cannot
turn analytics storage into a redirect dependency.

### Public-deployment gate

The prototype must not be exposed to public or untrusted traffic. If a future
project changes that boundary, the design must add rate limiting and quotas,
abuse and moderation controls, link disable/removal or expiration, backup
procedures, operational alerting, a multi-instance-capable database/control
plane, and validated capacity/SLO targets. These are future-product work, not
implicit features of this prototype.

## Architectural decisions

### ADR-001: Modular monolith

Separate services were rejected because the 2–3 day prototype has no independent scaling or deployment requirement. Internal module boundaries preserve future extraction options.

### ADR-002: Spring Boot, React, SQLite

This combination matches the approved stack and local-first requirement. It favors familiarity and maintainability over the smallest possible runtime.

### ADR-003: Spring JDBC instead of JPA

The model has few tables and simple queries. JDBC avoids ORM complexity and SQLite-specific behavior.

### ADR-004: Secure random Base62 codes

Random codes avoid exposing sequence volume and require only a database uniqueness constraint for coordination. Collision retries must be bounded and tested.

### ADR-005: Configured public origin

Short URLs use an explicit configured origin. Host and forwarded headers are not trusted by default to prevent host-header poisoning.

### ADR-006: SQLite deployment boundary

SQLite is the local source of truth, but the application is single-instance only. PostgreSQL and distributed controls are prerequisites for public/multi-instance deployment.

### ADR-007: HTTP 302 redirects

302 avoids permanent browser/proxy caching while redirect behavior may evolve. Conservative cache headers are required.

### ADR-008: No public deployment without abuse controls

Anonymous creation is unsafe without rate limiting, quotas, moderation, and link lifecycle controls. The MVP is local/demo-only until those controls are approved and implemented.

### ADR-009: Versioned migration ownership

Flyway migrations are the single schema-evolution mechanism. Ad hoc startup DDL,
ORM-generated schema changes, and repository-side table creation are rejected
because they make clean installs, upgrades, and rollback analysis ambiguous.

### ADR-010: Prototype-to-public-service boundary

The prototype optimizes for a small, inspectable local system. Its interfaces
and repository boundary should remain production-evolvable, but hosted
production capability is not assumed or required. Any future public service
must be separately designed, tested, and approved.

### ADR-011: Local prototype deployment

The local evaluation environment runs one Spring Boot process, one SQLite file, and one
separately served React/Vite frontend. PostgreSQL, containers, cloud resources,
distributed controls, and permanent hosting are rejected for this prototype
because they add deployment complexity without improving the evaluated local
behavior. The code should remain easy to replace or extend later through its
repository and module boundaries.

## Correctness, reliability, and optional optimizations

**Correctness:** validation, unique indexed codes, durable persistence before success, correct 302 `Location`, stable errors, and UTC timestamps.

**Reliability:** migration validation, bounded collision retries, health checks, controlled database failures, restart persistence, and failure tests.

**Required validation:** URL-security cases (schemes, credentials, controls,
ports, and encoding), concurrent creation, collision exhaustion, database
failure handling, clean/latest migration paths, configured-origin behavior,
CORS restrictions, and request/log redaction.

**Optional/future:** redirect caching, WAL tuning, aggregate operational metrics,
tracing, hosted analytics and retention, PostgreSQL migration, distributed rate
limiting, and horizontal scaling. The local T25 analytics and T24 orchestration
modules are implemented capabilities within the approved boundary.

## Known limitations

- Anonymous creation is abuse-prone.
- SQLite does not support high write concurrency or multiple application instances.
- There is no link deletion, disabling, expiration, or ownership. Analytics has
  no identity tracking, retention/deletion operation, dashboard, delivery
  guarantee, or public-service isolation.
- Destination safety is syntactic only.
- Hosted production operations are not included; local correctness and
  diagnostics are included.
