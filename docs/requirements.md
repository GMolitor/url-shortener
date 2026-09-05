# URL Shortener Requirements

## Problem

Provide a local-first URL-shortening service that accepts a valid destination URL, generates a compact short code, persists the mapping, and redirects visitors from the short URL to the original destination.

## Functional requirements

- Anonymous users can submit an HTTP or HTTPS URL.
- The service generates and persists a unique short code.
- Visiting a known short URL returns an HTTP 302 redirect.
- Unknown codes return a controlled 404 response.
- The React UI provides submission, validation, loading, errors, result display, and copy-to-clipboard behavior.
- The API remains independently usable from the UI.
- Authentication, aliases, expiration, editing, deletion, and link ownership are
  excluded from the MVP. T18/T25 add the approved local analytics capability
  described below; hosted analytics, identity tracking, and retention operations
  remain deferred.

## Non-functional requirements

- Backend: Java/Spring Boot.
- Frontend: React with npm tooling.
- Persistence: SQLite with Spring JDBC.
- Local setup requires no external services.
- Configuration supports server port, database path, and public origin overrides.
- The service is a production-quality, single-instance local prototype. Public hosting and multi-instance operation are out of scope.

## Capacity and prototype boundary

The implementation is a local-first production-ready prototype. Its baseline
targets are intentionally limited to local development and trusted demo use on
one application instance. The prototype expects production-quality correctness,
security, maintainability, testing, and reproducible setup within this local
boundary. It does not require public hosting or production infrastructure.

The prototype boundary is:

- At most 10 concurrent clients and 100 link-creation requests per minute.
- At most 1,000 persisted links in the local prototype database.
- For a warm local instance, creation should target p95 latency below 500 ms
  and redirects below 250 ms, excluding client/network startup overhead.
- The default deployment is bound to the local machine. It must not be exposed
  directly to the public internet or treated as a multi-instance service.
- The seven-character case-sensitive Base62 space contains 62^7 =
  3,521,614,606,208 possible codes. The 1,000-link target is a prototype
  operating bound, not a guarantee of public-service collision behavior.

Exceeding these limits, serving untrusted public traffic, or running multiple
backend instances requires a new approved capacity and deployment design. That
future design would need rate limiting and quotas, abuse/moderation controls,
link lifecycle management, durable backups, operational monitoring, and a
multi-instance-capable database/control plane.

## API requirements

- `POST /api/links` accepts `{ "url": "https://example.com" }` and returns `201 Created` with code, short URL, destination URL, and UTC creation timestamp.
- `GET /{code}` returns `302 Found` and a `Location` header for known codes.
- `GET /api/analytics/{code}` returns a total and hourly UTC buckets for a valid
  short code and time window. It does not expose destination or client identity
  data.
- `GET /actuator/health` reports application and SQLite health.
- API errors use a stable JSON envelope containing status, error code, message, request ID, and timestamp.
- JSON content type, request-body, and URL-length limits are enforced.
- `PUBLIC_ORIGIN` and `FRONTEND_ORIGIN` must be absolute HTTP(S) origins without credentials, paths, queries, fragments, or wildcards; invalid values fail startup.

## Data requirements

- Link records contain an internal ID, unique code, destination URL, and UTC creation timestamp.
- Code generation uses a cryptographically secure random Base62 generator.
- Codes are case-sensitive and initially seven characters unless a later approved decision changes the capacity bound.
- Database uniqueness is the collision authority; collision retries are bounded.
- Versioned migrations initialize and evolve the schema.

### Local analytics requirements

- A successful lookup publishes one event containing the case-sensitive short
  code and the UTC occurrence time.
- Event persistence is asynchronous behind a bounded queue. Queue pressure or
  analytics storage failure must not delay, fail, or change the redirect
  response; an event may be lost under those failure conditions.
- The aggregate API accepts optional RFC-3339 `from` and `to` parameters. The
  default window is the preceding 24 hours, the window must be ordered and no
  longer than 366 days, and the implementation permits `to` up to five seconds
  ahead of its current UTC clock.
- Analytics data is local SQLite data. The event model contains no IP address,
  user agent, destination URL, or client identity. No automated retention or
  deletion operation is implemented; local data remains subject to the
  database reset/backup procedures.

The complete HTTP contract is [`openapi.yaml`](openapi.yaml). Implementations
must conform to that contract, including the stable error envelope, request
limits, redirect headers, and configured-origin behavior.

## Security requirements

- Accept only HTTP and HTTPS schemes.
- Reject malformed URLs, embedded credentials, control characters, and oversized input.
- Never fetch or proxy destination URLs.
- Use an explicitly configured public origin; do not trust arbitrary host headers.
- Restrict CORS to the configured local frontend origin.
- Do not expose stack traces, SQL details, secrets, or full destination URLs in logs.
- Anonymous creation requires rate limiting before any public deployment; the
  prototype is not a public deployment.

## Reliability and observability

- Mappings survive application restarts.
- Database and migration failures produce controlled responses and actionable logs.
- Request IDs, structured access/failure logs, timing, and health checks are required.
- Concurrency and collision behavior must be tested.
- Caching, tracing, distributed rate limiting, backups, hosted analytics,
  identity tracking, retention operations, and public-service monitoring are
  deferred optimizations or production follow-up. The local T25 analytics
  event and aggregate API are implemented, but are not a public analytics
  service or operational dashboard.

## Explicit assumptions

- Anonymous, single-instance local prototype.
- Generated codes only; each valid create request creates a new mapping.
- HTTP 302 redirects with conservative caching behavior.
- Maximum URL length is 2048 characters.
- Public origin is configuration, not request-derived state.
- React and Spring run as separate local development processes.
- Redirect analytics are best-effort local observations, not a redirect
  correctness or delivery guarantee.
- The capacity and latency targets above are planning targets for validation,
  not a production service-level objective.
- Public deployment is out of scope for the prototype; anonymous rate limiting
  is therefore deferred in code and remains a prerequisite only for a future
  public service.

## Ambiguities and disposition

| Question | Classification | Prototype disposition |
|---|---|---|
| Authentication and ownership | DEFERRED | Anonymous usage only |
| Custom aliases | DEFERRED | Generated codes only |
| Expiration | DEFERRED | Links do not expire |
| Duplicate destination handling | ASSUMPTION | Always create a new mapping |
| Code format | ASSUMPTION | Seven-character case-sensitive Base62 |
| Redirect code | ASSUMPTION | HTTP 302 |
| Rate limiting | DEFERRED for this local prototype; required for public deployment | Document local-only restriction; require controls before public use |
| Analytics | IMPLEMENTED locally under approved T18/T25 exception | Code/time click events, bounded non-blocking persistence, and aggregate reads; hosted analytics and retention remain deferred |
| Deployment scale | ASSUMPTION | Local single instance |

## Acceptance criteria

- A clean checkout starts backend and frontend using documented commands.
- A valid URL can be submitted through the API and UI.
- A returned short URL redirects correctly.
- Mappings survive backend restart.
- Invalid URLs and oversized requests fail with documented errors.
- Unknown codes return controlled 404 responses.
- Health checks report application and SQLite status.
- Tests, formatting, linting, and frontend build pass.
- No secrets, databases, build output, or business functionality are committed accidentally.
- The documented capacity and prototype boundary are reviewed and explicitly
  approved before final prototype handoff. T17 is complete and approved.

## Brownfield review evidence

The final brownfield review on 2026-09-03 verified the documented backend and
frontend commands in the working tree. The full backend test suite and
`spotlessCheck` passed; `npm ci`, 11 frontend tests, lint, format, and build
passed. The focused 1,000-link persistence check passed in 21.885 seconds for
the Gradle invocation (2.785 seconds reported by the test case). This is
right-sized local evidence, not a public load test or service-level objective.
