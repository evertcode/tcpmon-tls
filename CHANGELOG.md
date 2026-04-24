# Changelog

## [0.6.8] - 2026-04-24

### Features

- **Configurable operational logging** — added SLF4J/Logback based runtime logging with `--log-level`, `--log-format`, `--access-log`, and `--metrics-log` options. Logs now cover startup, route binding, route changes, replay completion, TLS warnings, control plane metrics, and recoverable proxy errors without logging payloads or secrets
- **Body viewer code folding** — JSON and XML payload viewers now support folding blocks from the line gutter. JSON objects/arrays and XML nodes can be collapsed and expanded without modifying the underlying body content
- **Full body hydration on detail load** — opening a request detail now automatically fetches complete request/response bodies when the API response contains a truncated preview, avoiding the normal need to click `Load full body`
- **Structured JSON exchange exports** — JSON downloads now embed valid JSON request/response bodies as real JSON values instead of escaped strings, while XML/text bodies remain readable strings. XML downloads continue using CDATA for body readability

### UI improvements

- **Request filter toolbar alignment** — the request search field and method/status/page-size selectors now stay aligned on desktop, with more width reserved for the search field and compact widths for the selects
- **Payload action menu** — request replay actions remain visible while secondary actions (`Copy as cURL`, `Download JSON`, `Download XML`) moved into a compact `More` menu to reduce visual noise
- **Request/response detail alignment** — request cards now reserve equivalent spacing after the `Start line` label so headers and body sections align visually with response cards that show TTFB

## [0.6.7] - 2026-04-23

### Features

- **Request and response body viewer** — the payload detail panel now renders captured bodies in a dedicated code-style viewer instead of a plain `<pre>`, with line numbers, basic JSON/XML highlighting, and in-place expansion when a body is truncated and the full content is loaded on demand

## [0.6.6] - 2026-04-22

### Features

- **JSON and XML exchange download** — two new buttons in the request action bar let the user download the active exchange as a structured file. Both formats include metadata (exportedAt, sessionId, targetAddress, startedAt, durationMs), the request (method, path, query, body) and the response (body). Bodies are fetched from the server when truncated so the download always contains the complete content
- **Self-hosted IBM Plex typography** — the control plane now ships local `IBM Plex Sans` and `IBM Plex Mono` webfonts, improving technical readability and giving the UI a more deliberate product identity without relying on third-party font CDNs
- **Persistent theme selector in the topbar** — the web UI now exposes explicit `Light`, `Auto`, and `Dark` theme controls in the topbar, persists the choice in local storage, and applies it on first paint to avoid theme flicker during page load

### Bug fixes

- **Pagination footer shows range instead of row count** — the footer now displays "Showing 11–20 of 300 requests" reflecting the actual position in the result set, derived from the cursor stack depth and page size
- **Theme changes transition smoothly** — switching between light and dark themes now animates key surfaces with a short transition while respecting `prefers-reduced-motion`

## [0.6.5] - 2026-04-22

### Features

- **Page size selector in request table** — a dropdown in the request toolbar lets users choose between 10, 25, 50, and 100 rows per page. Defaults to 10. Changing the value resets to the first page and reloads, consistent with the method and status code filters

### Bug fixes

- **Request table total and average duration fall back to request rows** — `buildRouteHeaderViewModel` now uses `requestRows.length` and `calculateAverageDuration(requestRows)` when server facets have not loaded yet, preventing the total from showing 0 on first render
- **GitHub Actions opt into Node.js 24** — added `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true` to both workflows ahead of the mandatory Node.js 24 migration on June 2nd 2026

## [0.6.4] - 2026-04-21

### Performance

- **Server-driven paginated request table** — the request list is now fetched from `/api/requests` using keyset cursor pagination instead of being derived client-side from all loaded sessions. Requests are filtered by method, status code, and full-text search on the server, and route-level aggregate stats (request count, average duration, error rate) are served from `/api/route-stats` without loading raw events
- **`session_exchanges` summary table** — a new SQLite table tracks per-exchange method, path, status code, size, and timing in real time as payload events arrive. `listSessions()` now returns this data directly without loading or decoding raw events. A one-time backfill rebuilds summaries from existing events on first use, and the backfill guard runs at most once per store lifetime
- **Lazy full-body loading** — response and request bodies are truncated to 16 KB in all API responses. The full content is fetched on demand from `/api/sessions/{id}/exchanges/{index}/body`, which queries only PAYLOAD events for the requested direction instead of loading the full session graph
- **Raw payload bytes stripped from API responses** — `exchange.base64` is no longer included in `/api/sessions/{id}` responses. Non-pending PAYLOAD events also have their `details.base64` field removed before serialisation, since the decoded body is available through exchange aggregation
- **Gzip compression for API responses** — JSON responses larger than 512 bytes are gzip-compressed when the client sends `Accept-Encoding: gzip`
- **HTTP message split check optimized** — `HttpMessageParser` now uses a lightweight start-line check to detect HTTP messages instead of invoking the full `PayloadInspector.inspectBytes()` pipeline per chunk

### Features

- **Server-side replay by session and exchange index** — `POST /api/replay` now accepts `{routeId, sessionId, exchangeIndex, destination}` and fetches the payload from the database, removing the need to pass raw base64 through the UI
- **Full-body copy and HAR export** — copying request/response bodies and exporting HAR files now transparently fetches the complete body from the server when `bodyTruncated` is set, so exports are never cut off at the preview limit

### Bug fixes

- **PAYLOAD SSE events no longer trigger a full session list refresh** — a new `scheduleRequestTableRefresh` path reloads only the active route's request rows when a PAYLOAD event arrives, avoiding unnecessary list redraws for unrelated routes
- **Copy-to-clipboard and copy-as-cURL respect body truncation** — both actions now call `resolveFullBody` before writing to the clipboard, fetching the complete body when the cached preview is truncated
- **`exchangeIndex` used before declaration in `buildPayloadCard`** — fixed a `ReferenceError` caused by referencing a `const` binding before its declaration in the same block

## [0.6.3] - 2026-03-17

### Bug fixes

- **Detail panel loads after auto-selection** — when the active session was null and the list refresh auto-selected the first request row, the detail panel now loads immediately instead of remaining empty until the next manual interaction
- **Per-exchange timestamps on keep-alive connections** — each HTTP exchange in a reused TCP connection is now stamped with the timestamp of the payload event that delivered it, so Exchange 0 and Exchange 1 carry distinct `startedAt` values rather than both sharing the timestamp of the last received chunk

## [0.6.2] - 2026-03-17

### Bug fixes

- **Request table updates live** — the request table now refreshes when the active session receives new data, so new HTTP exchanges appear without waiting for the session to close
- **Topbar request count** — the subtitle now shows the HTTP request count instead of the TCP session count, which previously understated traffic on keep-alive connections

### Performance

- **Halved DB reads on `/api/sessions`** — session details are now loaded once per session and shared between the session summary and request row aggregation, eliminating a full extra pass through the store on every list refresh
- **Eliminated per-event `routeIdForSession` queries** — the route ID is now cached in memory when a session opens, removing a synchronised `SELECT` from every payload and lifecycle event recorded during the session lifetime

## [0.6.1] - 2026-03-17

### Control plane fixes

- **Keep-alive request rows restored** — the request table now shows one row per HTTP exchange inside a reused connection instead of appearing to overwrite the previous request with the latest exchange
- **Request selection tracks exchange index** — selecting a row in the table now opens the correct exchange in the detail view, including keep-alive sessions with multiple requests on the same TCP connection
- **Route summaries stay route-based** — route health, live state, and pending counts still derive from connection/session summaries while the request table derives from expanded per-exchange rows

### Tests

- **Regression coverage for keep-alive summaries** — backend and frontend tests now cover per-exchange request rows for reused HTTP connections

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
