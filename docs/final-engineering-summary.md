# Final Engineering Summary (T21)

**Date:** 2026-09-04  
**Status:** Complete for the approved local/trusted-demo prototype boundary

## Executive summary

The repository delivers a runnable, local-first URL shortener with a React/Vite
frontend, Spring Boot API, SQLite persistence, and Flyway migrations. The core
service creates seven-character case-sensitive Base62 links, redirects known
codes with HTTP 302, exposes health and stable errors, and remains usable
independently of the UI.

The approved T18 reconciliation expanded that local boundary with two
repository-contained capabilities: T24 provides a durable orchestration control
plane, and T25 provides failure-isolated redirect analytics with aggregate
reads. T19 and T20 package the evidence and the greenfield, brownfield, and
ambiguous-requirement demonstrations. This summary consolidates those artifacts
without claiming a public deployment or a raw external agent-runtime export.

## Delivered capabilities

### URL shortener

- `POST /api/links` validates an HTTP/HTTPS destination, rejects credentials,
  controls, malformed hosts, invalid ports, oversized URLs, and oversized
  request bodies, then durably stores a generated code.
- `GET /{code}` returns `302 Found`, a configured-origin `Location`, and
  `Cache-Control: no-store`; unknown codes return a controlled 404.
- `GET /actuator/health` reports application and SQLite health.
- Secure random seven-character Base62 generation, database uniqueness, bounded
  collision retries, UTC timestamps, request IDs, CORS restrictions, and safe
  error/log redaction are implemented.
- The React/Vite UI provides accessible input, client-side usability
  validation, loading and failure states, result display, and clipboard
  feedback. The API remains independently usable.

### Analytics (T25)

Redirects publish a click event containing only the short code and UTC
occurrence time to a bounded asynchronous publisher. Queue-full and persistence
failures are isolated from redirect handling; the redirect contract is not
delayed or failed because analytics storage is unavailable. `GET
/api/analytics/{code}` returns a total and hourly time buckets for a validated
time window of up to 366 days.

The event data is local SQLite data with no client identity, IP address, or user
agent capture. There is no hosted analytics, retention job, identity tracking,
or operational analytics dashboard. A full queue or writer failure can lose an
analytics event by design; redirect correctness takes precedence.

### Orchestration (T24)

The repository contains a locally runnable orchestration control plane with
durable SQLite state for runs, tasks, dependencies, attempts, approvals, graph
versions, audit events, and terminal per-run metrics. It supports:

- dependency and fallback graph validation, cycle detection, and readiness;
- entry, high-impact task, and exit approval gates;
- worker task claiming and result submission with ownership checks;
- bounded attempts (one through three), retry classification, and fallback
  activation;
- cancellation/safe-stop, rollback metadata, active-attempt closure, and
  audit events;
- policy guardrails against public hosting and other out-of-scope expansion;
  and
- versioned dynamic replanning with stale-version conflict detection.

Dependencies provide sequential ordering and synchronization while independent
tasks can be claimed concurrently. The service records state and evidence; it
does not execute work itself.

## Architecture and boundary

The retained architecture is a modular monolith:

```text
Browser -> React/Vite UI -> Spring Boot API -> SQLite file
                                      |
                                      +-> request IDs, logs, health
                                      +-> analytics event publisher
                                      +-> orchestration control-plane API
```

Spring JDBC isolates SQL, Flyway owns schema evolution, and `V1__create_links.sql`
owns link data. `V2__orchestration_and_analytics.sql` adds the orchestration
and analytics tables. The local workflow uses one backend instance, one SQLite
file, and a separately served frontend; no external service is required.

The approved prototype boundary is local/trusted-demo use: at most 10
concurrent clients, 100 creates per minute, and 1,000 persisted links, with
warm-instance planning targets of p95 below 500 ms for creation and 250 ms for
redirects. SQLite is a single-instance source of truth. `PUBLIC_ORIGIN` is
configured and validated; request `Host` and forwarded headers are not trusted
to construct returned short URLs.

This is not a public-service approval. Public exposure would require a separate
design and approval for rate limiting and quotas, abuse and destination
moderation, ownership and lifecycle controls, backups and recovery, operational
monitoring, a multi-instance database/control plane, and public capacity/SLO
evidence.

## Assignment-to-artifact traceability

| Assignment concern | Delivered behavior or evidence | Primary artifacts | Status and boundary |
|---|---|---|---|
| Runnable URL shortener | Create, persist, redirect, health, UI workflow, validation, and stable errors | [`requirements.md`](requirements.md), [`openapi.yaml`](openapi.yaml), [`architecture.md`](architecture.md), [`runbook.md`](runbook.md), `backend/src/main/java/com/example/urlshortener/`, `frontend/src/` | Implemented and locally validated; single-instance prototype |
| Durable local persistence | Versioned links schema, restart persistence, uniqueness, atomic create behavior | [`data-model.md`](data-model.md), `backend/src/main/resources/db/migration/V1__create_links.sql`, repository tests | Implemented; SQLite is not a hosted multi-instance store |
| Orchestration layer | Durable runs/tasks, dependency graph, gates, claims/results, retries, fallback, rollback, safe-stop, policy, audit, metrics, and replanning | `backend/src/main/java/com/example/urlshortener/orchestration/`, `backend/src/main/resources/db/migration/V2__orchestration_and_analytics.sql`, [`orchestration-evidence.md`](orchestration-evidence.md) | T24 complete and tested; does not launch or supervise agents |
| Analytics | Non-blocking durable click events and time-window aggregate reads | `backend/src/main/java/com/example/urlshortener/analytics/`, `backend/src/test/java/com/example/urlshortener/integration/AnalyticsControllerIntegrationTest.java`, V2 migration, T25 evidence in [`assignment-reconciliation.md`](assignment-reconciliation.md) | T25 complete within local boundary; event loss under bounded failure is accepted |
| External agentic orchestration | Parent coordination, worker roles, approval, safe-stop, retry/wait behavior, and replanning are recorded as session evidence | [`assignment-reconciliation.md`](assignment-reconciliation.md), [`orchestration-evidence.md`](orchestration-evidence.md) | Demonstrated by approved session summary; raw runtime export is unavailable |
| Greenfield demonstration | Input, decomposition, reconstructed role map, dependencies, decisions, validation, outputs, and limits | [`scenario-demonstrations.md#1-greenfield-prototype-scenario`](scenario-demonstrations.md#1-greenfield-prototype-scenario), task plan | Evidence-based reconstruction, not an exported agent run |
| Brownfield demonstration | A01-A05 hardening, issue handling, approvals, checks, GO verdict, and A06/A07 gaps | [`scenario-demonstrations.md#2-brownfield-hardening-scenario`](scenario-demonstrations.md#2-brownfield-hardening-scenario), [`brownfield-readiness-plan.md`](brownfield-readiness-plan.md) | Prototype GO; A06 and A07 remain partial |
| Ambiguous requirement handling | T18 decision separated runtime orchestration from repository capability and approved local analytics | [`assignment-reconciliation.md#approval-gate`](assignment-reconciliation.md#approval-gate), [`scenario-demonstrations.md#3-ambiguous-requirement-scenario`](scenario-demonstrations.md#3-ambiguous-requirement-scenario) | Resolved by recorded human approval; T22 reconciled the dependent documents |
| Validation and reproducible handoff | Backend/frontend tests, formatting, lint, build, focused security and capacity evidence | [`runbook.md#testing-and-quality-checks`](runbook.md#testing-and-quality-checks), [`brownfield-readiness-plan.md#final-brownfield-review-status-2026-09-03`](brownfield-readiness-plan.md#final-brownfield-review-status-2026-09-03), [`tasks.md`](tasks.md) | Local checks passed; hosted CI and clean-checkout evidence remain gaps |
| Final summary and traceability | This consolidated summary and matrix | `docs/final-engineering-summary.md` | T21 and T22 complete; T23 complete and human-approved |

## Rationale and engineering decisions

- **Modular monolith:** Spring Boot, React/Vite, and SQLite satisfy the local
  evaluation goal with inspectable boundaries and no deployment overhead.
- **SQLite plus Spring JDBC:** the data set and local concurrency target are
  small; repository-owned SQL and Flyway provide explicit, reproducible schema
  behavior.
- **Configured public origin:** generated URLs must not be derived from
  untrusted host or forwarding headers.
- **Random Base62 plus database uniqueness:** this avoids exposing sequence
  volume while retaining the database as collision authority and keeping
  retries bounded.
- **302 and `no-store`:** redirects remain conservative while link behavior
  can evolve.
- **Bounded asynchronous analytics:** analytics satisfy the approved assignment
  exception without allowing storage failure or queue pressure to break the
  primary redirect path.
- **Repository service plus runtime evidence:** T18 correctly identified that
  the external runtime demonstrates session coordination but is not itself a
  repository deliverable. T24 therefore supplies an executable local control
  plane while T19 preserves the distinction.
- **Scope control:** T24/T25 were approved as local additions only. Public
  hosting, authentication, abuse controls, and other deferred product concerns
  were not inferred from the assignment.

## Validation evidence and commands

The documented local evidence reports success for:

```text
backend/gradlew.bat test
backend/gradlew.bat spotlessCheck
frontend/npm.cmd ci
frontend/npm.cmd test                 (11 tests in the final brownfield evidence)
frontend/npm.cmd run lint
frontend/npm.cmd run format
frontend/npm.cmd run build
```

Additional focused evidence includes:

```text
backend/gradlew.bat test --tests com.example.urlshortener.integration.SecurityValidationIntegrationTest
```

The brownfield review also records passing 10-client concurrent-create and
1,000-link persistence checks. The focused 1,000-link Gradle invocation took
21.885 seconds, with 2.785 seconds reported by the test case. T24/T25
integration evidence covers graph validation, synchronization, approval
rejection, retries, fallback, cancellation, rollback, metrics, replanning,
analytics aggregation, failure isolation, and restart persistence. These are
local correctness and prototype-capacity checks, not public load, hosted CI,
disaster-recovery, or production-SLO evidence.

## Risks, assumptions, limitations, and trade-offs

- Anonymous link creation has no rate limiting or quota. Destination checks
  are syntactic, and loopback/private destinations are intentionally accepted
  for local use. This is unsafe for public traffic.
- SQLite, one backend instance, and the stated small capacity are deliberate
  prototype assumptions. There is no multi-instance coordination, backup
  schedule, restore drill, corruption recovery, hosted monitoring, or alerting.
- T24 approval, claim, result, stop, rollback, and replan endpoints have no
  authentication or authorization. Actor and worker strings are recorded but
  are not identities. There is no queue, scheduler, lease/heartbeat,
  timeout, backoff, abandoned-worker recovery, fairness, or compensating
  rollback action.
- T24 metrics are terminal per-run summaries, not aggregate operational
  metrics, SLO evaluation, dashboards, or alerts.
- T25 stores local code/time events only. There is no retention or deletion
  operation, and bounded asynchronous failure may drop analytics events.
- The external agent runtime has no raw run ID, worker transcript, prompt
  history, event export, or imported repository run record in this workspace;
  reconstructed scenarios must not be read as exact historical scheduling.
- The local analytics capability does not provide identity tracking, retention
  or deletion, a dashboard, or guaranteed event delivery. These remain explicit
  limitations even after the T22 documentation reconciliation.

## Remaining work and evidence gaps

- **A06 remains partial:** local checks and right-sized capacity evidence pass,
  but there is no hosted CI run, no clean checkout of the uncommitted state,
  and no separate dependency-review result.
- **A07 remains partial:** the documentation and handoff evidence is recorded,
  but the A06 clean-checkout/CI and dependency-review gaps prevent claiming
  complete final consistency/handoff work.
- **T22 is complete:** the documentation quality pass reconciled the local
  analytics/orchestration capabilities, OpenAPI contracts, links, commands,
  task references, and visible typography/encoding defects.
- **T23 is complete and human-approved:** the independent review recorded the
  application-code scope, open evidence gaps, approved exceptions, and
  conditional local-prototype verdict. See
  [`final-release-review.md`](final-release-review.md) for the review record.

T21 changes documentation only. T22 subsequently reconciled the documentation
set; neither task alters application code or converts the local prototype into
a public service. T23 is recorded separately as the independent release
review, with its final human approval and residual conditions in
[`final-release-review.md`](final-release-review.md).
