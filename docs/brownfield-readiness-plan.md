# Brownfield Production-Quality Prototype Plan

## Purpose and scope

This document coordinates the remaining brownfield work for the production-ready
prototype. The goal is production-quality engineering within a local,
clean-checkout evaluation environment. It is not a plan to host the service on
the public internet.

For this prototype, “production quality” means correct and secure behavior at
the documented local boundary, maintainable architecture, bounded resource use,
reproducible setup, useful diagnostics, strong tests, and clear documentation.
It does not require a permanent database service, cloud infrastructure, TLS
termination, multi-instance deployment, on-call operations, or disaster
recovery infrastructure.

Audit/scope update: 2026-09-03  
T17 status: **Complete and approved by the human reviewer.**  
Code changes made by this planning update: none.

## Final brownfield review status (2026-09-03)

The final review verified the current working-tree implementation and tests
against the approved T01–T17 contracts without reopening or repeating those
tasks. The verdict is **GO for the local/trusted-demo prototype**. This is not
a public-service approval.

| Area | Status | Evidence and disposition |
|---|---|---|
| A01 request-body limit | **Complete** | Fixed-length, exact-limit, chunked-over-limit, and read-failure tests; full backend suite passed. The filter retains at most 4,097 bytes. |
| A02 atomic persistence | **Complete** | SQLite `INSERT ... RETURNING id` inside a transaction; collision and simulated generated-key failure rollback tests passed. |
| A03 API/config boundaries | **Complete** | Origin startup validation, configured-origin/Host handling, CORS, unsupported method/path/media, and stable-envelope tests passed. |
| A04 redirect/destination security | **Complete** | Existing security validation covers hostile URL inputs, encoded controls, no-fetch loopback redirect, origin manipulation, redaction, case-sensitive codes, and `no-store`; documentation now states private/loopback acceptance is intentional. |
| A05 frontend/local integration | **Complete** | `npm ci`, 11 Vitest tests, lint, format, and build passed; Vite remains a local proxy with separate processes. |
| A06 reproducibility/quality | **Partial** | Backend/frontend documented checks passed; 10-client concurrency and 1,000-link persistence passed. The focused 1,000-link invocation took 21.885 seconds (2.785 seconds test time). A hosted CI job, clean checkout of the uncommitted state, and a separate dependency-review step were not run/present. |
| A07 documentation/handoff | **Partial** | Requirements, architecture, OpenAPI, runbook, tasks, handoff, and this plan were synchronized and evidence was recorded. A07 is not claimed complete because clean-checkout/CI evidence and the A06 dependency-review decision remain open. |

The final review's early-rejection logging finding is resolved: `AccessLogFilter`
now wraps `RequestIdFilter`, so body-limit/read-failure responses are
access-logged with the same safe fields as normal requests, without duplicate
events for requests that continue through the chain.

### Release verdicts

- **Prototype handoff:** **GO** for the approved local/trusted-demo boundary.
  T17’s clean-checkout, dependency review, acceptance checklist, and go/no-go
  decision are accepted as complete.
- **Brownfield hardening:** targeted follow-up tasks below remain useful for
  correctness and code quality. They should be prioritized by project risk
  and risk, not by an assumption of public hosting.
- **Public service:** out of scope. The existing rate-limiting, moderation,
  lifecycle, multi-instance, backup, and hosted-deployment limitations remain
  documented rather than becoming implementation work for this prototype.

## Updated architectural boundary

Keep the current modular monolith:

    Browser -> React/Vite UI -> Spring Boot API -> SQLite file
                                          |
                                          v
                                  request IDs/logs/health

The expected local evaluation workflow is:

1. Clone or open the repository on an evaluator’s computer.
2. Run the documented backend and frontend commands.
3. Exercise the API/UI and tests locally.

SQLite is therefore an appropriate durable local database. The separate React
development server and Spring Boot server remain acceptable. Do not add
PostgreSQL, Docker, cloud resources, a distributed cache, a hosted frontend,
or another service solely to simulate production deployment.

The architectural refactoring boundary is narrow: improve request-boundary
resource control, persistence atomicity, configuration validation, contract
handling, and testability while preserving the existing module boundaries and
approved MVP API unless a task explicitly changes the contract.

## Baseline evidence

The validation run for the approved T17 baseline completed successfully:

| Check | Result |
|---|---|
| 'backend/gradlew.bat test' | Passed |
| 'backend/gradlew.bat spotlessCheck' | Passed |
| 'frontend/npm test' | Passed: 9 tests |
| 'frontend/npm run lint' | Passed |
| 'frontend/npm run format' | Passed |
| 'frontend/npm run build' | Passed |

The backend tests cover the prototype HTTP flow, SQLite persistence, restart
behavior, small-scale concurrency, URL validation, configured origin, CORS,
redaction, health, and bounded retries. The frontend tests cover the create
workflow and clipboard outcomes. These are appropriate prototype evidence;
they are not public-service capacity or disaster-recovery evidence.

## Findings and disposition

| ID | Finding | Prototype disposition | Evidence / follow-up |
|---|---|---|---|
| F-01 | The 4,096-byte request guard relied on declared 'Content-Length'; chunked input could bypass the early filter. | **Completed by A01.** | 'RequestIdFilter.java' and request-boundary tests |
| F-02 | Repository 'save' inserted and then performed a separate lookup for the ID. | **Completed by A02.** | 'JdbcLinkRepository.java' and rollback test |
| F-03 | 'PUBLIC_ORIGIN' and 'FRONTEND_ORIGIN' had no explicit startup validation contract. | **Completed by A03.** | 'OriginValidator.java', startup tests, and synchronized docs |
| F-04 | Framework-generated failures such as method mismatches and unmapped API paths were not fully specified by the stable error contract. | **Completed by A03 for the in-scope routes.** | 'ApiExceptionHandler.java', controller tests, and 'openapi.yaml' |
| F-05 | Destination validation is syntactic and permits loopback/private destinations; no reputation screening exists. | **Accepted prototype boundary.** Keep the no-fetch guarantee and document that public abuse screening is excluded. | 'UrlPolicy.java', architecture ADR-008; A04 documentation/tests |
| F-06 | Anonymous creation has no rate limiting or quotas. | **Public-only deferred work; not required for local evaluation.** | Requirements/runbook limitation; deferred public-service work |
| F-07 | No link disable/delete/expiration/ownership/takedown workflow exists. | **Public/product-only deferred work; do not add speculative features.** | 'Link.java', 'V1__create_links.sql'; deferred public-service work |
| F-08 | SQLite is single-instance and unsuitable for hosted multi-instance scale. | **Accepted prototype boundary.** Keep SQLite and test migration/restart behavior. | Architecture ADR-006; A06 |
| F-09 | No production backup schedule, retention, restore drill, RPO/RTO, or corruption response exists. | **Out of scope.** Local database backup/reset instructions are sufficient. | 'runbook.md'; deferred public-service work |
| F-10 | Request IDs, access logs, and health exist; production metrics, alerting, and log shipping do not. | **Local baseline accepted; the early-rejection logging gap is resolved by the final review follow-up.** | 'AccessLogFilter.java', 'RequestIdFilter.java', 'application.yml'; A06 |
| F-11 | No production packaging, TLS/proxy deployment, secret manager, or hosted actuator policy exists. | **Out of scope.** Require reproducible local commands and safe defaults instead. | CI/application config; A06 |
| F-12 | Dependencies are pinned/locked, but CI has no extensive vulnerability/license/provenance pipeline. | **Partial A06 hygiene.** Inputs are reproducible enough for local checks, but no separate lightweight dependency-review step exists. | 'build.gradle', 'package-lock.json', '.github/workflows/ci.yml'; A06 |
| F-13 | The production build/topology for separately run frontend and backend is not a hosted deployment specification. | **Accepted prototype boundary.** Verify the local Vite proxy and build instructions. | 'vite.config.ts', 'README.md', 'runbook.md'; A05/A06 |
| F-14 | Existing concurrency tests are correctness checks at the prototype boundary, not public load tests. | **Right-sized validation required:** verify the stated local bounds and avoid infrastructure-heavy load tooling. | 'HttpSqliteIntegrationTest.java', requirements; A06 |

## Agent-ready work breakdown

These tasks are the remaining prototype-quality work. They are deliberately
smaller than a public-production program and can be assigned independently.

### A01 — Enforce the request-body limit for all request transfer modes

**Owner:** backend/security  
**Depends on:** T17 complete; preserve the OpenAPI 4,096-byte limit

Replace the declared-length-only behavior with bounded actual-byte enforcement
for POST /api/links. Avoid unbounded buffering, preserve request IDs, and
return the existing JSON 413 envelope. Cover fixed-length, chunked, malformed,
exact-limit, and over-limit requests.

**Acceptance criteria:**

- A chunked body over 4,096 bytes cannot reach JSON deserialization or
  persistence.
- The response remains 413 REQUEST_TOO_LARGE with all stable envelope fields.
- Tests demonstrate bounded behavior and no regression in valid requests.

### A02 — Make create persistence atomic and failure-safe

**Owner:** backend/persistence  
**Depends on:** T03/T06 contracts; coordinate with A01 only for integration tests

Review the insert-plus-follow-up-read sequence and choose the smallest safe
refactor: reliable generated-key retrieval, a transaction with correct retry
boundaries, or another SQLite-compatible approach. Preserve database uniqueness
as the collision authority and do not introduce a new database.

**Acceptance criteria:**

- A successful create response corresponds to a durable record with its ID.
- A simulated post-insert read/key failure has intentional rollback or
  idempotent behavior, covered by a test.
- Collision retries, concurrent creates, and non-collision storage failures
  retain their current classifications and bounds.

### A03 — Harden API and configuration boundaries

**Owner:** backend/API  
**Depends on:** T04/T08; A01 for complete request-limit behavior

Validate configured public/frontend origins at startup (absolute origin,
permitted scheme, no credentials/query/fragment/wildcard) and review framework
failure paths against the OpenAPI error contract. Keep public URLs independent
of Host and Forwarded headers. Avoid adding a large validation framework for
these few settings.

**Acceptance criteria:**

- Invalid configuration fails early with an actionable safe message.
- In-scope API failures have stable JSON content type, status, error code,
  timestamp, and request ID.
- Tests cover origin manipulation, CORS allow/deny, unsupported methods/media,
  unknown API paths, and response/log redaction.

### A04 — Preserve and document destination/redirect security

**Owner:** backend/security/documentation  
**Depends on:** T07/T08

Do not add public moderation or network reputation services. Instead, lock down
the prototype boundary with tests and documentation: HTTP/HTTPS only,
credentials/control rejection, URL length, no destination fetch/proxy, safe
configured origin, case-sensitive codes, and conservative redirect caching.
Record private/loopback acceptance as intentional local behavior.

**Acceptance criteria:**

- Security tests cover hostile schemes, credentials, controls, malformed hosts,
  invalid ports, encoded input, and origin-header manipulation.
- A redirect never performs an outbound destination request.
- Requirements, architecture, OpenAPI, and runbook agree on the boundary.

### A05 — Verify the local frontend/backend integration

**Owner:** frontend  
**Depends on:** T09/T11; A03 if API error behavior changes

Keep the Vite proxy and separate local processes. Add or refine lightweight
tests only for observable local behavior: validation, loading, stable server
errors including 413/429/503 if represented, result display, copy feedback,
and a reachable short URL.

**Acceptance criteria:**

- npm ci, npm test, npm run lint, npm run format, and npm run build work from a
  clean checkout.
- No production hosting configuration or localhost-independent cloud setup is
  required.
- Accessibility and failure-state behavior remain covered.

### A06 — Reproducible prototype and right-sized quality checks

**Owner:** build/CI  
**Depends on:** T17 baseline; coordinate with A01–A05 as they land

Keep CI focused on clean-checkout reproducibility: pinned Gradle/npm inputs,
backend tests/formatting, frontend tests/lint/format/build, secret/artifact
exclusion, and a lightweight dependency review suitable for an interview
repository. Add no hosting or infrastructure pipeline.

**Acceptance criteria:**

- CI reproduces the documented checks from a clean checkout.
- No database files, build output, .env secrets, or dependency caches are
  tracked.
- Local capacity validation covers up to 10 concurrent clients, 100 creates per
  minute, and 1,000 persisted links where practical; results are documented as
  prototype limits, not public-service SLOs.

### A07 — Documentation consistency and final prototype handoff

**Owner:** documentation/release reviewer  
**Depends on:** A01–A06 as applicable

Keep the requirements, architecture, data model, OpenAPI, README, runbook,
tasks, and handoff guide synchronized. Include the local commands, environment
variables, SQLite reset/backup guidance, known limitations, test evidence, and
the explicit decision not to add hosted production infrastructure.

**Acceptance criteria:**

- A reviewer can run the project from a clean checkout using only documented
  prerequisites and commands.
- No document implies that the prototype has a public deployment, permanent
  database, or production SLO.
- The handoff reports changed files, tests, decisions, warnings, and risks.

## Explicitly deferred public-service work

Do not assign these as implementation tasks for this prototype:

- public rate limiting, quotas, abuse moderation, reputation services, and
  anonymous-traffic identity controls;
- link ownership, authenticated administration, takedown, deletion, disabling,
  expiration, hosted analytics, analytics identity tracking, or audit retention
  beyond the approved local capability;
- PostgreSQL or another hosted multi-instance database/control plane;
- cloud/container deployment, TLS/proxy infrastructure, secret management,
  autoscaling, operational alerting, log shipping, backups, restore drills, or
  disaster recovery;
- public capacity SLOs, distributed tracing, or production load infrastructure.

These remain valid future-product concerns and should stay visible in the
limitations/architecture documentation. Their absence is not a defect against
the prototype’s stated local boundary.

## Dependency graph

    T17 (complete/approved)
      |
      +--> A01 request-body hardening
      +--> A02 persistence atomicity
      +--> A03 API/config boundary
      +--> A04 security-boundary verification
      +--> A05 frontend/local integration
      +--> A06 reproducible CI and right-sized validation
                  |
                  +--> A07 documentation and prototype handoff

A01–A06 can proceed in parallel where their contracts do not conflict. A02
should be reviewed before changing repository abstractions. A03 must update
OpenAPI/tests if framework error behavior changes. A07 is the final consistency
pass; T17 itself is already approved and must not be reopened.

## Human decisions already resolved by this scope update

- The deliverable is evaluated locally and is not hosted.
- SQLite and separate local frontend/backend processes are retained.
- Public-service controls and infrastructure are documented as deferred, not
  silently implemented.
- T17 is complete and approved.

The only remaining choices are prioritization choices for A01–A07 or an
explicit requirement to add one of the deferred public-service items.
