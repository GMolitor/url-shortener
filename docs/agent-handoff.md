# Agent Handoff Guide

## Source of truth

Read these files before changing behavior:

1. [`requirements.md`](requirements.md)
2. [`architecture.md`](architecture.md)
3. [`adversarial-review.md`](adversarial-review.md)
4. [`tasks.md`](tasks.md)
5. The root [`README.md`](../README.md) for runnable commands

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
[`openapi.yaml`](openapi.yaml). It
defines create, redirect, and health operations; payloads; stable errors;
validation/body limits; 302 and `no-store` redirect behavior; and the
configured public-origin and CORS boundary. T05/T08/T09 may implement against
it without changing the contract.
