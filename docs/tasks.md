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
```

## Tasks

- **T01 Requirements baseline**: confirm scope, assumptions, capacity, and deployment boundary. **Approved.**
- **T02 Architecture finalization**: incorporate adversarial findings and close Critical/High issues. **Approved.**
- **T03 Data model**: define schema, indexes, constraints, timestamps, code capacity, and versioned migrations. **Complete.**
- **T04 API definitions**: publish OpenAPI, payloads, error envelope, validation, redirect, and public-origin behavior. **Approved.**
- **T05 Project foundation**: establish build, frontend, configuration, migration framework, health, tooling, and docs. This is `BOOTSTRAP-001`.
- **T06 Persistence**: implement link repository, mapping migration, SQLite behavior, and persistence tests.
- **T07 Domain/code generation**: implement URL policy, secure codes, reserved paths, and bounded retries.
- **T08 Backend API**: implement creation, redirect, health, validation, error handling, and configured-origin behavior.
- **T09 React UI**: implement the approved create-link workflow and failure states.
- **T10 Backend unit tests**: cover validation, generator, collision, and error branches.
- **T11 Frontend tests**: cover UI states, API outcomes, accessibility, and clipboard behavior.
- **T12 Integration/E2E**: cover HTTP-to-SQLite behavior, restart persistence, concurrency, proxying, and smoke flow.
- **T13 Security validation**: adversarial input, limits, CORS, host handling, redaction, and abuse-boundary tests.
- **T14 Reliability/observability**: failure injection, health, request IDs, logs, retries, and shutdown validation.
- **T15 Code review**: independent requirements, architecture, security, scope, and test-evidence review. Human approval required.
- **T16 Documentation**: setup, API, architecture, limitations, operations, and testing runbook.
- **T17 Release readiness**: clean-checkout verification, dependency review, acceptance checklist, and go/no-go. Human approval required.

## Parallel groups

- After T02: T03 and initial T04 analysis.
- After T03/T04: T05 and T07 can proceed in parallel.
- After T05: T06 and T09 can proceed where their contracts permit.
- After implementation: T10, T11, and portions of T14 can proceed in parallel.
- After validation evidence: T15 and T16 can proceed in parallel.

## Critical path

`T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T08 -> T12 -> T13 -> T15 -> T17`

No task may expand the MVP to authentication, aliases, expiration, analytics, public deployment, or unrelated infrastructure without a new approved task.

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
