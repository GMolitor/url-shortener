# URL Shortener

Local-first, production-ready URL-shortener prototype implemented as a Spring Boot
backend and React/Vite frontend. The backend accepts validated HTTP/HTTPS
destinations, stores generated short codes in SQLite, and redirects visitors
from known codes. The API is independently usable from the frontend.

This is a production-ready prototype. It is intentionally
restricted to local or trusted-demo use on one backend instance; it is not
intended to be hosted or exposed as a public service.

## Engineering documentation

- [Requirements](docs/requirements.md)
- [Architecture and ADRs](docs/architecture.md)
- [Data model](docs/data-model.md)
- [OpenAPI contract](docs/openapi.yaml)
- [Operations and testing runbook](docs/runbook.md)
- [Adversarial architecture review](docs/adversarial-review.md)
- [Task plan and dependency graph](docs/tasks.md)
- [Brownfield production-readiness plan](docs/brownfield-readiness-plan.md)
- [Agent handoff guide](docs/agent-handoff.md)

## Current capabilities

- `POST /api/links` validates and persists a destination, then returns a
  seven-character case-sensitive Base62 code and configured short URL.
- `GET /{code}` returns an HTTP 302 redirect for a known code and a controlled
  JSON 404 response for an unknown code.
- `GET /actuator/health` reports application and SQLite health.
- URL validation rejects unsupported schemes, malformed URLs, credentials,
  control characters, invalid ports, and URLs longer than 2,048 characters.
- SQLite persistence uses the Flyway `V1__create_links.sql` migration.
- Backend tests cover API behavior, URL policy, code generation, persistence,
  migrations, restart behavior, and collision handling.

The frontend provides the complete React/Vite create-link workflow governed by
the approved requirements and OpenAPI contract.

## Prerequisites

- Java 25
- Node.js 24 and npm

## Run locally

Start the backend from `backend`:

```powershell
.\gradlew.bat bootRun
```

Start the frontend from `frontend`:

```powershell
npm ci
npm run dev
```

By default, the backend listens on `http://localhost:8080` and the frontend
on `http://localhost:5173`.

## Configuration

- `SERVER_PORT`: backend port; default `8080`
- `DATABASE_PATH`: SQLite file path; default `./data/url-shortener.sqlite`
- `PUBLIC_ORIGIN`: origin used to construct returned short URLs; default
  `http://localhost:8080`
- `FRONTEND_ORIGIN`: sole allowed frontend CORS origin; default
  `http://localhost:5173`

Both origins must be absolute HTTP(S) origins without credentials, paths,
queries, fragments, or wildcards; invalid values fail backend startup.

The backend binds to `127.0.0.1` by default. Flyway validates and runs
migrations at startup. Do not edit an already-applied migration or disable
validation. If a local database has a migration checksum mismatch, stop the
backend, back up and remove that local database, then restart it.

## Validate changes

Backend, from `backend`:

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
```

Frontend, from `frontend`:

```powershell
npm ci
npm test
npm run lint
npm run format
npm run build
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

## Prototype boundary and future public-service work

The prototype is limited to one local/trusted-demo instance, 10 concurrent
clients, 100 link creations per minute, and 1,000 persisted links. It must not
be exposed directly to the public internet. Anonymous rate limiting, quotas,
abuse and moderation controls, link lifecycle management, backups, operational
alerting, multi-instance database support, and public capacity targets are
future-service requirements, not requirements for this local prototype.
Production quality here means correct local behavior, security at the stated
boundary, maintainable code, reproducible setup, and thorough validation.
