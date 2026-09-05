# Engineering Task Plan

## Dependency graph

```text
T01 Requirements -> T02 Architecture
T02 -> T03 Data model -> T04 API definitions
T02,T04 -> T05 Project foundation
T03,T05 -> T06 Persistence
T01,T02,T03 -> T07 Domain/code generation
T04,T06,T07 -> T08 Backend API/redirects
T04,T05 -> T09 React UI
T07,T08 -> T10 Backend unit tests
T09 -> T11 Frontend tests
T05,T06,T08,T09,T10,T11 -> T12 Integration/E2E
T02,T04,T08,T12 -> T13 Security validation
T06,T08,T12 -> T14 Reliability/observability validation
T10,T11,T12,T13,T14 -> T15 Code review
T04,T12,T13,T14 -> T16 Documentation
T15,T16 -> T17 Release readiness
T17 -> T18 -> T24,T25
T24,T25 -> T19,T20
T19,T20 -> T21 -> T22 -> T23
```

## Tasks

- **T01 Requirements baseline**: confirm scope, assumptions, capacity, and deployment boundary. **Approved.**
- **T02 Architecture finalization**: incorporate adversarial findings and close Critical/High issues. **Approved.**
- **T03 Data model**: define schema, indexes, constraints, timestamps, code capacity, and versioned migrations. **Complete.**
- **T04 API definitions**: publish OpenAPI, payloads, error envelope, validation, redirect, and public-origin behavior. **Approved.**
- **T05 Project foundation**: establish build, frontend, configuration, migration framework, health, tooling, and docs. **Complete.**
- **T06 Persistence**: implement link repository, mapping migration, SQLite behavior, and persistence tests. **Complete.**
- **T07 Domain/code generation**: implement URL policy, secure codes, reserved paths, and bounded retries. **Complete.**
- **T08 Backend API**: implement creation, redirect, health, validation, error handling, and configured-origin behavior. **Complete.**
- **T09 React UI**: implement the approved create-link workflow and failure states. **Complete.**
- **T10 Backend unit tests**: cover validation, generator, collision, and error branches. **Complete.**
- **T11 Frontend tests**: cover UI states, API outcomes, accessibility, and clipboard behavior. **Complete.**
- **T12 Integration/E2E**: cover HTTP-to-SQLite behavior, restart persistence, concurrency, proxying, and smoke flow. **Complete.**
- **T13 Security validation**: adversarial input, limits, CORS, host handling, redaction, and abuse-boundary tests. **Complete.**
- **T14 Reliability/observability**: failure injection, health, request IDs, logs, retries, and shutdown validation. **Complete.**
- **T15 Code review**: independent requirements, architecture, security, scope, and test-evidence review. **Approved.**
- **T16 Documentation**: setup, API, architecture, limitations, operations, and testing runbook. **Complete.**
- **T17 Release readiness**: clean-checkout verification, dependency review, acceptance checklist, and go/no-go. **Complete and approved 2026-09-03.**
- **T18 Assignment reconciliation**: decide and record how the external Orchestration Agent satisfies the assignment's orchestration-layer requirement, and explicitly decide whether analytics are implemented, demonstrated as deferred, or recorded as an assignment gap. **Approved 2026-09-04; repository-contained implementation authorized.**
- **T24 Orchestration service implementation**: implement the local repository-contained orchestration control plane with durable run/task state, dependency graphs, entry/exit gates, sequential and parallel execution with synchronization, approvals, bounded retries, fallback, rollback, safe-stop, policy guardrails, audit events, reliability metrics, and dynamic replanning. **Complete 2026-09-04; implementation and full validation passed.**
- **T25 Analytics implementation**: implement redirect analytics with durable event capture that cannot delay or break redirects, aggregate read APIs/UI as appropriate, tests, and documented retention/privacy boundaries. **Complete 2026-09-04; implementation and full validation passed.**
- **T19 Orchestration evidence package**: document the Orchestration Agent's lifecycle, dependency graph, gates, sequential and parallel paths, synchronization, context and decision lineage, human approvals, retries, fallback, rollback, safe-stop behavior, policy guardrails, audit observability, reliability metrics, and dynamic replanning. Attach representative run records or templates where available; identify capabilities handled manually or not evidenced. **Complete 2026-09-04; evidence package in [orchestration-evidence.md](orchestration-evidence.md).**
- **T20 Scenario demonstrations**: produce case studies for the greenfield prototype, brownfield hardening, and an ambiguous requirement. Each must show input, decomposition, assigned agents, dependencies, decisions, issue handling, approvals, validation, outputs, and limitations. **Complete 2026-09-04; evidence package in [scenario-demonstrations.md](scenario-demonstrations.md).**
- **T21 Final engineering summary and traceability**: add an assignment-to-artifact traceability matrix and a single final engineering summary covering rationale, architecture, artifacts, risks, trade-offs, validation, assumptions, limitations, and the prototype-versus-public-service boundary. Link it from the README. **Complete 2026-09-04; summary in [final-engineering-summary.md](final-engineering-summary.md).**
- **T22 Documentation quality pass**: reconcile README, requirements, architecture, runbook, handoff, task statuses, and OpenAPI references; remove stale claims; verify links and commands; correct visible encoding/typography defects; and state the orchestration boundary and analytics decision consistently. **Complete 2026-09-04; documentation links, OpenAPI YAML parsing, and diff validation passed.**
- **T23 Documentation release review**: independently review T18-T22 against the assignment PDF, confirm no application code was changed, record open evidence gaps and approved scope exceptions, and obtain final human approval. **Complete and approved 2026-09-04.**

## Parallel groups

- After T02: T03 and initial T04 analysis.
- After T03/T04: T05 and T07 can proceed in parallel.
- After T05: T06 and T09 can proceed where their contracts permit.
- After implementation: T10, T11, and portions of T14 can proceed in parallel.
- After validation evidence: T15 and T16 can proceed in parallel.

## Critical path

`T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T08 -> T12 -> T13 -> T15 -> T17`

No task may expand the MVP to authentication, aliases, expiration, analytics, public deployment, or unrelated infrastructure without a new approved task.

The assignment-reconciliation work now authorizes T24/T25 implementation within
the existing Spring Boot/React/SQLite local boundary. T24/T25 must not add
public hosting, cloud infrastructure, or deferred public-service controls.
T19/T20 remain evidence and demonstration work and must distinguish repository
service behavior from the external agent runtime used to execute this task.

## T01 handoff and approval gate

T01 establishes the following baseline in `requirements.md`:

- local/trusted-demo use only, with one backend instance;
- planning bounds of 10 concurrent clients, 100 creates per minute, and 1,000
  persisted links;
- warm-instance targets of p95 under 500 ms for creation and 250 ms for
  redirects; and
- seven-character Base62 capacity of 3,521,614,606,208 codes.

The human approver accepted these bounds and the prototype-versus-production
distinction on 2026-08-31. T02 may now incorporate the approved boundary and close
the related adversarial-review findings. If the bounds are revised, downstream
data-model, generator, persistence, and validation tasks must be rechecked.

## T02 handoff and approval gate

The architecture now records the prototype-to-production boundary, configured
origin and CORS trust rules, Flyway migration ownership, repository and SQLite
limits, bounded collision behavior, centralized validation/error handling,
required observability, and production-readiness prerequisites. Human approval
was recorded on 2026-08-31; T03 and T04 may consume this architecture.

## T03 handoff

`docs/data-model.md` defines the single-table MVP schema and is the contract
for T05/T06. T05 should establish Flyway infrastructure without inventing a
different schema; T06 should implement and test `V1__create_links.sql`, the
repository operations, uniqueness behavior, restart persistence, and migration
failure handling.

## T04 handoff and approval gate

The API contract is published in `docs/openapi.yaml`. T05, T08, and T09 must
consume it without changing status codes, field names, error codes, limits,
redirect caching, CORS, or configured-origin semantics. Human approval is
required before those tasks treat the contract as final.
Approval was recorded on 2026-08-31; T05, T08, and T09 may now implement
against the contract.

## T05–T09 implementation status

- **T05 complete:** Spring Boot/Gradle, React/Vite, SQLite, Flyway, actuator
  health, configuration overrides, and project tooling are established.
- **T06 complete:** `V1__create_links.sql`, the `Link` model, JDBC repository,
  SQLite constraints, and persistence tests are implemented.
- **T07 complete:** HTTP/HTTPS URL policy, secure seven-character Base62 codes,
  reserved route prefixes, and bounded collision retries are implemented and
  unit tested.
- **T08 complete:** create and redirect endpoints, configured-origin links,
  request IDs, stable errors, JSON/body validation, CORS, and SQLite health are
  implemented and tested. The request-size filter enforces the limit for both
  declared-length and chunked requests.
- **T09 complete:** the React create-link workflow includes accessible input,
  client-side usability validation, loading and failure states, result display,
  and clipboard feedback. Frontend tests, lint, formatting, and production
  build pass.

## T13 handoff and validation status

T13 is complete. `SecurityValidationIntegrationTest` covers hostile URL
schemes, credentials, malformed hosts, control-character encodings, URL and
request-body limits, configured-origin behavior despite Host and forwarded
headers, no-fetch redirect behavior for a loopback destination, CORS allow and
deny cases, and response redaction. Existing exception-handler tests cover SQL,
stack-trace, collision, and secret redaction.

Validation run:

- `backend/gradlew.bat test --tests com.example.urlshortener.integration.SecurityValidationIntegrationTest` — passed.
- `backend/gradlew.bat spotlessCheck` — passed.

Residual issues are documented in the approved prototype boundary: anonymous
creation has no rate limiting or abuse controls. These remain blockers for
public deployment and are not part of T13 or the brownfield prototype scope.

## T15 review and approval

The independent T15 review found no additional implementation defects in the
local prototype. Human approval was granted on 2026-09-01. Its brownfield
follow-up items are tracked in `brownfield-readiness-plan.md`; A01–A05 changes
and their tests are reviewed in the final handoff below. Public deployment
remains out of scope.

## T16 handoff

The documentation set is current through the implemented prototype. The
README covers orientation and quick setup; `docs/runbook.md` is the detailed
setup, API, operations, troubleshooting, limitations, and testing guide. T17
verified the commands and clean-checkout instructions during release
readiness.

## T17 handoff and approval

T17 is complete. The human reviewer approved the prototype result after the
clean-checkout verification, dependency review, acceptance checklist, and
go/no-go review. The approved release boundary is local/trusted-demo use on
one backend instance; this prototype is not intended
to be hosted publicly.

## Brownfield prototype-quality handoff

The brownfield audit, findings, architectural refactoring guidance, and
agent-ready follow-up tasks A01–A07 are documented in
[`brownfield-readiness-plan.md`](brownfield-readiness-plan.md).

The prototype handoff is approved. A01–A05 are complete; A06 and A07 remain
partial as recorded in the final brownfield review because current evidence
does not include a hosted CI run or clean checkout of the uncommitted state.
These tasks do not authorize adding a permanent database, hosted deployment,
public rate limiting, moderation, lifecycle management, or disaster-recovery
infrastructure. Those remain documented future-product concerns.

## Final brownfield review handoff

The 2026-09-03 final review found the local prototype behavior suitable for a
GO verdict, with A06 and A07 evidence caveats recorded in the brownfield plan.
The current working-tree changes are brownfield-only: request-body enforcement,
atomic persistence, origin/API hardening, frontend failure handling, tests, and
documentation. No T01–T17 work was reopened or repeated.
