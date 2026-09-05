# Agent Handoff Guide

## Source of truth

Read these files before changing behavior:

1. [`requirements.md`](requirements.md)
2. [`architecture.md`](architecture.md)
3. [`adversarial-review.md`](adversarial-review.md)
4. [`tasks.md`](tasks.md)
5. The root [`README.md`](../README.md) for runnable commands
6. The [`brownfield-readiness-plan.md`](brownfield-readiness-plan.md) for
   prototype-quality follow-up tasks and deferred public-service concerns

## Working rules

- Work only within the assigned task scope.
- Inspect the repository and verify task assumptions before editing.
- Stop on a contradiction with an approved requirement or ADR.
- Do not silently resolve a blocking ambiguity.
- Add tests with behavior changes.
- Add concise behavior comments/Javadocs for non-obvious core logic, especially
  security, validation, persistence, concurrency, retries, and HTTP boundaries;
  explain why and preserve comments as behavior evolves.
- Run relevant tests, formatting, and linting before handoff.
- Review the complete diff and report every changed file.
- Never delete databases, user files, or unrelated work without explicit approval.
- Do not add dependencies unless the task explicitly requires them and the architecture permits them.

## Handoff format

```text
STATUS:
FILES_CHANGED:
TESTS_RUN:
TEST_RESULTS:
DECISIONS:
WARNINGS:
RISKS:
```

## Current bootstrap boundary

The bootstrap establishes only project structure, configuration, migration infrastructure, health, tests, tooling, and documentation. It intentionally does not contain URL creation, code generation, redirects, analytics, authentication, rate limiting, or business tables.

## T01 status

The requirements baseline now distinguishes the initial greenfield scenario
prototype from the eventual production-ready product. It includes explicit
prototype capacity targets and a local-only, single-instance deployment
boundary. Human approval was recorded on 2026-08-31; T02 architecture work may
begin. Do not interpret the prototype targets as production SLOs or as
authorization to expose the service publicly.

## T02 status

The architecture has been finalized and approved on 2026-08-31.
It closes the architectural dispositions for origin trust, CORS, migration
ownership, SQLite scope, bounded retries, validation/error boundaries,
observability, and the prototype-to-production gate. T03 and T04 may proceed;
they must preserve these constraints and publish their own
implementation contracts.

## T03 status

The data-model contract is complete in [`data-model.md`](data-model.md). It
defines the `links` table, binary/case-sensitive seven-character Base62 code,
2048-character destination limit, UTC timestamp format, unique lookup
authority, bounded collision behavior, and Flyway migration rules. T05/T06
must implement from that contract and add the specified persistence and
migration tests.

## T04 status

The API contract was approved on 2026-08-31 and is complete in
[`openapi.yaml`](openapi.yaml). It defines create, redirect, health, local
analytics, and local orchestration operations; payloads; stable errors;
validation/body limits; 302 and `no-store` redirect behavior; and the
configured public-origin and CORS boundary. T05/T08/T09 implemented the
original link contract; T24/T25 added the approved local operations.

## T14 status

T14 is complete. The backend emits correlation-safe structured access logs with
method, path, status, duration, and request ID for requests reaching the access
log filter, and logs storage and unexpected failures without exception
messages, destination URLs, or SQL details. Request IDs are assigned before downstream filters and returned in the response header
and error envelope. Actuator reports application and SQLite health, code
allocation retries remain bounded, and graceful shutdown is enabled with a
ten-second shutdown phase. Failure handling, health, and shutdown behavior are
covered by backend tests, including storage-failure injection and context-close
validation.

## T15 status

The independent requirements, architecture, security, scope, and test-evidence
review is approved by the human approver on 2026-09-01. No additional defects
were found in the local prototype. Brownfield follow-up remains for enforcing
the request-body limit on chunked requests and maintaining documentation
consistency; both are accepted as non-urgent prototype limitations and remain
public-deployment blockers where applicable.

## T16 status

T16 is complete. The documentation set includes the current README, API
contract, architecture, data model, requirements, and the detailed
[`runbook.md`](runbook.md) for setup, operations, API usage, testing,
troubleshooting, and prototype limitations. T17 completed the final clean
checkout and command verification.

## Brownfield prototype-quality status

The prototype’s local release is approved. The audit, architectural
refactoring guidance, and independently assignable A01–A07 quality tasks are in
[`brownfield-readiness-plan.md`](brownfield-readiness-plan.md). T17 is complete;
do not add hosted production infrastructure or infer public-service scope from
the prototype’s use of the phrase production quality.

## Final brownfield review handoff (2026-09-03)

STATUS: GO for the local/trusted-demo prototype; A06 is partial and A07 is not
claimed complete until clean-checkout/CI evidence and the remaining quality-gap
decision are closed.

FILES_CHANGED: The working tree contains only brownfield backend/frontend test
and implementation changes plus documentation updates listed in the final
report. No T01–T17 work was reopened or repeated.

TESTS_RUN: `backend/gradlew.bat test`, `backend/gradlew.bat spotlessCheck`,
`frontend/npm.cmd ci`, `frontend/npm.cmd test`, `frontend/npm.cmd run lint`,
`frontend/npm.cmd run format`, `frontend/npm.cmd run build`, and the focused
`HttpSqliteIntegrationTest.persistsThePrototypeVolumeOfOneThousandLinks` test.

TEST_RESULTS: All commands passed. The focused 1,000-link Gradle invocation
took 21.885 seconds; the test case reported 2.785 seconds. The full suite
includes the 10-client concurrent create check and the new 1,000-link check.

DECISIONS: A01–A05 are accepted against the approved contracts. The prototype
remains local/trusted-demo, one-instance, SQLite-backed, and without public
rate limiting, moderation, lifecycle controls, or hosting infrastructure.

WARNINGS: The CI workflow is present and runs the documented backend/frontend
checks, but this review did not execute a hosted CI job or a clean checkout of
the uncommitted brownfield state. CI also has no separate lightweight
dependency-review step.

RISKS: Public deployment remains blocked by the documented abuse,
destination-safety, lifecycle, backup, operations, and multi-instance gaps.

## T18-T22 status at handoff

T18 was approved on 2026-09-04 and authorized repository-contained T24
orchestration and T25 local analytics within the existing local/trusted-demo,
single-instance SQLite boundary. T24, T25, T19, T20, and T21 are complete.

The T24 service durably coordinates runs, tasks, approvals, attempts, audit,
metrics, and replanning but does not execute work or connect to the external
agent runtime. T25 records best-effort short-code/UTC-time redirect events and
serves bounded hourly aggregates; queue/storage failure may lose events, and no
identity capture or automated retention/deletion exists.

T22 is the documentation consistency pass. It synchronizes those capabilities
into the requirements, architecture, data model, runbook, README, OpenAPI,
handoff, and task references without adding public-service behavior. At the
time of this handoff, T23 was not started; the current task record and
[`final-release-review.md`](final-release-review.md) now record T23 complete
and human-approved.
