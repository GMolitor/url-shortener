# Operations and Testing Runbook

This runbook describes the local/trusted-demo production-ready prototype. It is the
operational companion to the [requirements](requirements.md),
[architecture](architecture.md), and [OpenAPI contract](openapi.yaml).

## Local setup

### Prerequisites

- Java 25
- Node.js 24 and npm
- No external services; SQLite is file-backed and Flyway runs at backend startup

### Start the services

From `backend`, start Spring Boot:

```powershell
.\gradlew.bat bootRun
```

From `frontend`, install the locked dependencies and start Vite:

```powershell
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` and `/actuator` to the
backend at `http://localhost:8080` during development. The backend binds to
`127.0.0.1` by default.

### Configuration

Set these environment variables before starting the backend when overrides are
needed:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Backend listening port |
| `DATABASE_PATH` | `./data/url-shortener.sqlite` | SQLite database file |
| `PUBLIC_ORIGIN` | `http://localhost:8080` | Trusted origin used in returned short URLs |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Sole allowed CORS origin |

`PUBLIC_ORIGIN` is configuration, not derived from `Host` or forwarded headers.
Keep it aligned with the address users should receive in API responses.
Both origin settings are validated at startup and must be absolute HTTP(S)
origins without credentials, paths, queries, fragments, or wildcards. An
invalid value fails startup with the setting name in the diagnostic.

## API quick reference

The authoritative contract is [openapi.yaml](openapi.yaml). A smoke request
against the default backend:

```powershell
$response = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/links `
  -ContentType 'application/json' `
  -Body '{"url":"https://example.com/docs"}'
$response
```

The response contains `code`, `shortUrl`, `destinationUrl`, and UTC
`createdAt`. Visit the returned `shortUrl` or request it with redirects
disabled to inspect the `302 Location` response:

```powershell
curl.exe -i $response.shortUrl
```

Expected API behavior:

- `POST /api/links`: `201` after durable persistence.
- `GET /{code}`: `302` with `Location` and `Cache-Control: no-store` for a
  known seven-character Base62 code.
- `GET /api/analytics/{code}`: `200` with `code`, `totalClicks`, the resolved
  `from`/`to` instants, and hourly UTC `buckets`. Invalid codes or windows
  return `400`; a valid code with no events returns empty aggregates.
- Unknown codes: `404` with `CODE_NOT_FOUND`.
- Invalid JSON, fields, URLs, or code paths: `400` with the appropriate stable
  error code.
- Non-JSON create requests: `415` with `UNSUPPORTED_MEDIA_TYPE`.
- Requests over 4,096 bytes: `413` with `REQUEST_TOO_LARGE`, regardless of
  whether the request uses a declared length or chunked transfer encoding.
- Persistence failures: `500` with `STORAGE_FAILURE`.
- Code-allocation exhaustion: `503` with `SERVICE_UNAVAILABLE`.

Destination URLs are validated syntactically and stored as supplied; the
backend never resolves, fetches, previews, or proxies them. Loopback and
private-network destinations are intentionally accepted within this
local/trusted-demo boundary. They must not be treated as safe for a future
public deployment without a separately approved abuse and destination-safety
design.

Error responses contain `status`, `errorCode`, `message`, `requestId`, and
UTC `timestamp`. Messages and logs do not expose stack traces, SQL details,
secrets, or full destination URLs.

### Analytics operations

Redirect handling publishes a code/time event to a single background writer
through a 1,024-entry bounded queue. Publishing does not wait for SQLite and
does not change the `302` response. A full queue, writer exception, or forced
shutdown can drop an event; the writer logs only bounded diagnostic fields.

Call the aggregate API with optional RFC-3339 UTC parameters:

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/analytics/Abc1234?from=2026-09-03T00:00:00Z&to=2026-09-04T00:00:00Z'
```

With no parameters, the API uses the preceding 24 hours. `from` must precede
`to`, the window may be at most 366 days, and `to` may be up to five seconds
ahead of the server clock. The endpoint returns only short-code/time-derived
aggregates. It is unauthenticated like the other prototype APIs, has no client
identity or IP/user-agent capture, and has no retention or deletion operation.
Rows remain in the local SQLite file until that file is otherwise managed.

### Orchestration operations

The `/api/orchestration/runs` API is a repository-contained local control plane:

| Operation | Purpose |
|---|---|
| `POST /api/orchestration/runs` | Create a `PLANNED` run from a task graph; returns `201` and the run data |
| `GET /api/orchestration/runs/{runId}` | Read run, task, and approval state |
| `GET .../{runId}/ready-tasks` | List tasks currently in `READY` state |
| `POST .../{runId}/start` | Pass the entry gate and start an approved run |
| `POST .../{runId}/approvals` | Record `ENTRY`, `TASK`, or `EXIT` approval/rejection |
| `POST .../{runId}/tasks/{taskKey}/claim` | Claim a ready task for a worker ID |
| `POST .../{runId}/tasks/{taskKey}/result` | Submit the claiming worker's success/failure result |
| `POST .../{runId}/cancel` | Safe-stop a non-terminal run |
| `POST .../{runId}/rollback` | Record rollback state and metadata |
| `POST .../{runId}/replans` | Add validated tasks using the current graph version |
| `GET .../{runId}/attempts` and `GET .../{runId}/tasks/{taskKey}/attempts` | Read attempt history |
| `GET .../{runId}/audit-events` | Read bounded audit transitions |
| `GET .../{runId}/metrics` | Read terminal per-run metrics when a metrics row exists |

The full payload and response shapes are in [openapi.yaml](openapi.yaml).
The service validates graph cycles, fallback targets, task limits, policy terms,
and bounded text. It records state and evidence but does not execute actions.
Callers perform polling and work execution. There is no scheduler, queue,
worker process, authentication/authorization, lease or heartbeat, timeout,
automatic recovery, or external-agent runtime adapter. The runtime used for
this development session is documented separately in
[orchestration-evidence.md](orchestration-evidence.md).

## Health and observability

Check application and SQLite health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Healthy startup returns HTTP `200` with status `UP`; an unavailable database or
application component returns HTTP `503` with status `DOWN`. Every request
receives an `X-Request-Id` response header and an access log with the event,
method, path, status, duration, and request ID. Storage and unexpected failures
log the exception type and correlation fields without sensitive exception
messages.

## Database and migration operations

The database is created at `DATABASE_PATH` and migrations are applied by
Flyway. The current business migration is `V1__create_links.sql`, which owns
the `links` table and its constraints. `V2__orchestration_and_analytics.sql`
owns the local orchestration and analytics tables. Never edit an already-applied
migration.

For a checksum mismatch or intentionally disposable local database:

1. Stop the backend.
2. Copy the SQLite file to a safe backup location if its data matters.
3. Remove only the configured local database file (and its SQLite `-shm` or
   `-wal` sidecars if present).
4. Restart the backend and allow Flyway to recreate the schema.

For a useful local backup, stop the backend first so no write is in progress,
then copy the configured SQLite file. Restore only while the backend is
stopped. Production backup, integrity checking, retention, and corruption
recovery are not prototype requirements; they would be designed separately
before any public deployment.

## Shutdown and failure handling

Stop `bootRun` with `Ctrl+C`; Spring Boot uses graceful shutdown with a
ten-second shutdown phase. If startup fails, inspect the first migration or
datasource error, correct configuration or file permissions, and restart.

During shutdown the analytics writer attempts to drain queued events for up to
five seconds. A forced shutdown can discard the remaining queue; this is an
accepted local analytics limitation and does not block backend shutdown.

The prototype is single-instance. Do not point multiple backend processes at
the same SQLite file or expose the service to untrusted public traffic.
Anonymous creation has no rate limiting, quota, moderation, or link lifecycle
controls. These are limitations of the local prototype and would be release
blockers only for a future public service.

## Testing and quality checks

Run backend checks from `backend`:

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
```

Run frontend checks from `frontend`:

```powershell
npm ci
npm test
npm run lint
npm run format
npm run build
```

The backend suite covers URL policy, secure code generation, reserved paths,
collision retries, API/error behavior, CORS and origin handling, SQLite
persistence, migration constraints, restart persistence, concurrency, health,
failure handling, request/log redaction, analytics, and orchestration behavior.
The frontend suite covers the
accessible form, client validation, loading and error states, successful result
display, network fallback, and clipboard success/failure.

Before handing off a change:

1. Run the relevant focused tests while iterating.
2. Run the complete backend or frontend checks for the affected area.
3. Review the complete diff and confirm no database, build output, dependency
   cache, environment file, or secret is staged.
4. Update the relevant requirements, architecture, API, data-model, or
   operational documentation when behavior changes.

## Current limitations

- Prototype capacity is 10 concurrent clients, 100 creates per minute, and
  1,000 persisted links.
- Request-size rejection reads at most one byte beyond the 4,096-byte limit,
  so chunked requests cannot bypass the body cap.
- Anonymous creation is abuse-prone because rate limiting and quotas are not
  implemented.
- SQLite is not suitable for high write concurrency or multiple instances.
- Links cannot be deleted, disabled, expired, or assigned to an owner.
- Destination safety is syntactic; the service does not assess reputation.
- Analytics is local and best-effort: there is no identity tracking, retention
  or deletion job, dashboard, delivery guarantee, or public analytics service.
- Orchestration metrics are terminal per-run summaries rather than aggregate
  operational metrics; orchestration has no auth, scheduler, leases, timeouts,
  or worker recovery.
- Backups, tracing, distributed controls, and public deployment operations are
  outside the prototype.
