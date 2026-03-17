# Changelog

## [0.6.0] - 2026-03-17

### Control plane UI

- **Static web assets and modular frontend** — the control plane UI is now served from classpath assets instead of a single embedded Java string, with JavaScript split by domain (`state`, `api`, `routes`, `sessions`, `details`, `actions`, `route-modal`)
- **Centralized frontend state** — the UI now uses an explicit store (`uiState`, `getState`, `setState`, `patchState`) instead of ad hoc globals, making refresh, SSE updates, and selection flows more predictable
- **Safer DOM rendering** — major route, session, and detail panels no longer depend on large `innerHTML` blocks or inline event handlers; UI events are delegated through `data-action`
- **Route header redesign** — the selected route card now emphasizes operational context with route health metrics and active request context instead of a flat metadata row
- **Favicon and asset cleanup** — the web UI now ships a dedicated `favicon.svg`, and static assets are organized under `/assets/js` and `/assets/styles`

### Reliability and tests

- **Frontend helper test suite** — added `node --test` coverage for core web helpers and route-header derivation logic
- **Combined project validation** — `npm run test:all` runs frontend helper tests and Maven tests together, and CI workflows use that command before packaging
- **Live/request context fixes** — the route header now refreshes selected request and client context from the active session instead of staying pinned to the first route summary
- **Direction parsing fix** — client-to-target and target-to-client detection no longer rely on ambiguous substring matching, fixing TTFB and related UI calculations

## [0.5.0] - 2026-03-17

### Security

- **UI auth session for browser clients** — the control plane now supports `POST /api/auth/session` and stores UI authentication in an `HttpOnly` cookie; browser SSE no longer relies on `?token=` in the URL
- **Stronger control plane headers** — responses now include `Content-Security-Policy`, `Referrer-Policy`, and `Permissions-Policy`; the older `X-XSS-Protection` header was removed
- **Request and replay limits** — JSON request bodies and replay payloads are capped to reduce accidental or abusive oversized submissions
- **TLS secret preservation on route update** — editing a TLS route from the UI no longer clears stored keystore/truststore passwords when the password fields are left blank

### Reliability

- **Async session persistence off the Netty event loop** — payload and lifecycle writes are serialized through a dedicated writer so proxy traffic is less exposed to SQLite latency
- **Safer session store shutdown** — async writes are drained cleanly before SQLite closes, avoiding connection-closed races during tests and application shutdown
- **Bounded control plane executor** — the embedded HTTP server now uses a fixed-size executor with a bounded queue instead of an unbounded cached thread pool
- **Replay resource reuse** — replay requests now reuse a shared Netty event loop group instead of creating one per replay
- **Explicit SQLite schema versioning** — the database now uses `PRAGMA user_version` with deterministic migrations and legacy schema detection

### Operations

- **Runtime status endpoint** — `GET /api/runtime` exposes local operational state such as route count, session count, SSE clients, intercept mode, UI TLS status, and token configuration
- **Separated unit and integration test phases** — `mvn test` runs only unit tests while `mvn verify` runs `*IntegrationTest` via Failsafe
- **Coverage gate in verify** — JaCoCo now generates reports during `verify` and enforces an initial minimum coverage threshold for the bundle

## [0.4.0] - 2026-03-17

### Security

- **Bearer token authentication** — all `/api/*` endpoints reject requests without a valid `Authorization: Bearer <token>` header when `--ui-token` is set; the SSE endpoint also accepts `?token=` as a query parameter for browser `EventSource` clients that cannot set custom headers. When the flag is omitted, authentication is disabled (backward-compatible default)
- **HTTPS for the control plane** — `--ui-tls-keystore` (plus `--ui-tls-keystore-password` and `--ui-tls-keystore-type`) enables HTTPS on the web UI using the JDK `HttpsServer`; plain HTTP remains the default when the flag is absent
- **Encrypted passwords at rest** — keystore and truststore passwords stored in the SQLite `routes` table are now encrypted with AES-256-GCM; a 256-bit key is auto-generated on first run and written to `sessions/db.key` (POSIX permissions: `600`); existing plaintext values in older databases are read transparently (no migration needed)
- **Password masking in API** — `GET /api/config` no longer includes keystore or truststore passwords in its response payload
- **HTTP security headers** — every HTTP response now includes `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 1; mode=block`, and `Cache-Control: no-store`
- **Route input validation** — listener host, target host, and SNI host are validated against a safe character allowlist; values containing shell injection characters (`; | & \` $ < > " '`) are rejected with `HTTP 400`

---

## [0.3.0] - 2026-03-16

### Route management from the UI

- Routes are now created, edited, and deleted entirely from the web UI
- Routes persist in SQLite and reload automatically on restart — no config file needed
- The app starts with zero routes if the database is empty; this is not an error
- Add/Edit route modal redesigned:
  - TLS sections for listener and target, shown only when transport is `TLS`
  - SNI Host, Verify hostname, and Trust all certificates moved inside the target TLS section so they are hidden for `PLAIN` transport
  - Client Authentication field for listener TLS (`None`, `Optional`, `Require`)
  - Keystore type merged into a single row with file and password fields
  - `— or keystore —` separator between cert+key and keystore options to communicate they are alternatives
  - Subsection labels inside TLS panels (Server Certificate, Truststore, Client Certificate)
  - Section headers redesigned as styled dividers with a horizontal rule
  - Modal header border separating title from form content
  - Modal widened to 620px max

### Config file

- Config file now manages application-level settings only: `ui`, `sessionsDir`, `interceptMode`, `tlsProtocols`, `tlsCiphers`
- `listener`, `target`, and `routes[]` fields removed from the config file schema
- All `--listen-*` and `--target-*` CLI flags removed

### Backend

- `routes` table extended with 16 nullable TLS material columns via idempotent `ALTER TABLE` migration (safe against existing databases)
- `SessionStore` persists and reloads full TLS material (cert, key, keystore, truststore paths and passwords) for each route
- `ControlPlaneServer` accepts and returns TLS material in route create/update payloads
- `TlsContextFactory.buildClientContext` handles null `TlsMaterial` to support target TLS with `insecureTrustAll` and no client certificate

### Bug fixes

- Route header showed only `→ target:port` before any requests were made; it now always shows `listener:port → target:port`
- SNI Host was not pre-filled when opening the Edit modal
- `Ctrl+C` sometimes failed to terminate the process: replaced `syncUninterruptibly()` with `await(timeout)` in `TcpMonProxy.close` so the shutdown hook returns even if Netty worker threads crash during buffer pool cleanup

---

## [0.2.0] - 2026-03-14

### Control plane — UI redesign

- Replaced request UUID column with Method + Path columns in the request table
- Color-coded HTTP status badges (2xx green, 3xx blue, 4xx amber, 5xx red)
- Route sidebar cards now show status border accent and latest request preview
- Route overview redesigned with stat blocks (Total, Open, Pending)
- Intercept panel surfaces pending payloads prominently above the timeline
- Headers section and timeline expanded by default
- Payload card: headers reordered above body
- Loading overlay during session detail fetch
- Relative timestamps (just now, 4m ago, 2h ago)
- Pending count visual alarm (amber pill / pulsing red pill at ≥3)
- Dark mode via `prefers-color-scheme`
- Empty state with onboarding hint

### Control plane — actionable insights

**Export & sharing**
- HAR export (HAR 1.2) for all sessions in the active route
- Copy as cURL for any captured request

**TLS visibility**
- TLS panel per session showing inbound and outbound protocol, cipher suite, and SNI

**Performance metrics**
- TTFB calculated from event timestamps, shown in the response card header (color-coded: green / amber / red)
- Duration and response size columns in the request table
- Average duration and error count summary in sidebar route cards
- Session timing waterfall replacing the raw event list: TLS inbound, TLS outbound, wait (TTFB), download, and total

**Multi-exchange**
- Exchange diff: side-by-side comparison of status codes and differing headers between exchanges

**Configuration visibility**
- `GET /api/config` endpoint exposing proxy routes, transport modes, and intercept mode (no credentials)
- Config panel accessible from the topbar

### Backend

- `GET /api/config` endpoint added to `ControlPlaneServer`
- Session summaries now include `durationMs` and `responseSizeBytes`
- `ControlPlaneServer` constructor receives `ProxyConfig` instead of `UiConfig` directly

### Bug fixes

- Copy body and Copy headers buttons broke when payload content contained single quotes — resolved by reading from session state instead of embedding data in HTML `onclick` attributes

---

## [0.1.0] - 2026-02-28

Initial release.

- TLS and mTLS proxy with configurable listener and target
- HTTP/1.1 payload parsing (chunked, gzip, brotli, deflate)
- Intercept mode: hold payloads for inspection and forwarding with optional editing
- Replay: resend captured requests to listener or target directly
- Session persistence in SQLite
- Multi-route support
- Live session updates over SSE
- Web control plane with request table, payload viewer, and session timeline
- YAML configuration file support
