# Changelog

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
