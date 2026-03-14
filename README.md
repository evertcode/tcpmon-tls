# tcpmon-tls

`tcpmon`-style proxy for modern HTTP/TLS debugging.

`tcpmon-tls` is a Java tool for debugging local and remote integrations over TCP, TLS, and HTTP/HTTPS. It lets you inspect `request/response` traffic, intercept payloads, edit HTTP requests, resend them to the target, recapture them through the local listener, and run multiple routes in a single process.

## Highlights

- multiple listeners and targets per process using `routes[]`
- HTTP `request/response` inspection from a local web UI
- interception, structured editing, and forwarding of requests
- replay to the target and recapture through the local listener
- `TLS` and `mTLS` support for inbound and outbound connections
- `JSON` or `YAML` configuration and executable `jar` packaging

## Typical use cases

- debug `local HTTP -> remote HTTPS`
- inspect requests and responses without changing the client
- reproduce integration failures from captured traffic
- validate `TLS/mTLS` connectivity to remote backends
- run several local routes against different targets

The most useful and tested flows today are:

- `local HTTP -> remote HTTPS`
- `local HTTP -> remote HTTP`
- `local TLS -> remote TLS`
- request recapture from the local UI

## What it does

- exposes a local TCP or TLS listener
- forwards traffic to a TCP or TLS target
- supports inbound and outbound `mTLS`
- persists session history locally
- exposes a local web UI for inspection
- separates HTTP `request` and `response` messages when possible
- supports multiple HTTP exchanges within a single keep-alive session
- can resend a request:
  - to the local listener for recapture
  - directly to the configured target

## Requirements

- Java 21
- Maven 3.9+ to build

## Build

```bash
mvn -q package -DskipTests
```

Resulting jar:

```text
target/tcpmon-tls-0.1.0-SNAPSHOT.jar
```

## Quick start

Generate an example config file:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --init-config tcpmon.json
```

You can also generate it as `YAML`:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --init-config tcpmon.yaml
```

Start the proxy using that file:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

## Recommended case: local HTTP -> remote HTTPS

Example using `jsonplaceholder`:

```json
{
  "listener": {
    "host": "127.0.0.1",
    "port": 9000,
    "mode": "PLAIN"
  },
  "target": {
    "host": "jsonplaceholder.typicode.com",
    "port": 443,
    "mode": "TLS",
    "sni": "jsonplaceholder.typicode.com",
    "insecure": true,
    "rewriteHostHeader": true
  },
  "ui": {
    "host": "127.0.0.1",
    "port": 8080,
    "enabled": true
  },
  "sessionsDir": "./sessions",
  "interceptMode": "NONE",
  "tlsProtocols": ["TLSv1.3", "TLSv1.2"]
}
```

The same example in `YAML`:

```yaml
listener:
  host: 127.0.0.1
  port: 9000
  mode: PLAIN
target:
  host: jsonplaceholder.typicode.com
  port: 443
  mode: TLS
  sni: jsonplaceholder.typicode.com
  insecure: true
  rewriteHostHeader: true
ui:
  host: 127.0.0.1
  port: 8080
  enabled: true
sessionsDir: ./sessions
interceptMode: NONE
tlsProtocols:
  - TLSv1.3
  - TLSv1.2
```

Start it:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

Local test:

```bash
curl -v http://127.0.0.1:9000/posts/1
```

Local UI:

```text
http://127.0.0.1:8080/
```

## Running with CLI flags

The same example without a config file:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar \
  --listen-host 127.0.0.1 \
  --listen-port 9000 \
  --listen-mode PLAIN \
  --target-host jsonplaceholder.typicode.com \
  --target-port 443 \
  --target-mode TLS \
  --target-sni=jsonplaceholder.typicode.com \
  --target-insecure=true \
  --rewrite-host-header=true \
  --ui-enabled=true \
  --ui-host 127.0.0.1 \
  --ui-port 8080
```

## Most important options

### Local listener

- `--listen-host`
  Local bind address for the proxy listener. Use `127.0.0.1` for local-only access or `0.0.0.0` to accept connections from other hosts.
- `--listen-port`
  Local port where clients connect to `tcpmon-tls`.
- `--listen-mode=PLAIN|TLS`
  Controls whether the local listener accepts plain TCP/HTTP or TLS/HTTPS connections.
- `--listen-client-auth=NONE|OPTIONAL|REQUIRE`
  Enables client-certificate validation on the local listener. Use `REQUIRE` when you want inbound `mTLS`.
- `--listen-cert`
  PEM certificate presented by the local TLS listener.
- `--listen-key`
  PEM private key that matches `--listen-cert`.
- `--listen-keystore`
  Alternative to PEM files when the local listener certificate is stored in `JKS` or `PKCS12`.
- `--listen-truststore`
  Trust material used to validate client certificates when inbound client auth is enabled.

### Remote target

- `--target-host`
  Hostname or IP of the remote backend that receives forwarded traffic.
- `--target-port`
  Remote backend port.
- `--target-mode=PLAIN|TLS`
  Controls whether the outbound connection to the backend is plain TCP/HTTP or TLS/HTTPS.
- `--target-sni`
  Hostname announced in the outbound TLS handshake. Useful when connecting by IP or when the backend uses TLS virtual hosting.
- `--target-insecure`
  Disables outbound certificate validation. Intended for local testing only.
- `--target-verify-hostname`
  Enables hostname verification for outbound TLS. Use this when you want stricter remote certificate validation.
- `--rewrite-host-header`
  Rewrites the HTTP `Host` header to match the configured target. Useful when clients connect to `localhost` but the backend expects its own hostname.
- `--target-cert`
  PEM client certificate for outbound `mTLS`.
- `--target-key`
  PEM private key for `--target-cert`.
- `--target-keystore`
  Alternative to PEM files when the outbound client certificate is stored in `JKS` or `PKCS12`.
- `--target-truststore`
  Trust material used to validate the remote server certificate.

### UI and sessions

- `--ui-enabled`
  Enables the local control-plane UI.
- `--ui-host`
  Bind address for the local web UI.
- `--ui-port`
  Port used by the local web UI.
- `--sessions-dir`
  Directory where session history is persisted. The SQLite database is created here as `sessions.db`.
- `--intercept-mode=NONE|REQUEST|RESPONSE|BOTH`
  Chooses which traffic direction is paused for manual forward/edit in the UI.

### File-based configuration

- `--config <path>`
  Loads runtime configuration from a `JSON` or `YAML` file.
- `--init-config <path>`
  Writes an example config file to the given path. The output format is inferred from the extension, for example `.json`, `.yaml`, or `.yml`.

File-based configuration supports both `JSON` and `YAML`, in two modes:

- simple mode: `listener` + `target`
- multi-route mode: `routes[]`

## Multi-route in a single process

You can define multiple listeners, each with its own target, within the same process.

Example:

```json
{
  "routes": [
    {
      "id": "public-api",
      "listener": {
        "host": "127.0.0.1",
        "port": 9000,
        "mode": "PLAIN"
      },
      "target": {
        "host": "jsonplaceholder.typicode.com",
        "port": 443,
        "mode": "TLS",
        "sni": "jsonplaceholder.typicode.com",
        "insecure": true,
        "rewriteHostHeader": true
      }
    },
    {
      "id": "legacy-http",
      "listener": {
        "host": "127.0.0.1",
        "port": 9001,
        "mode": "PLAIN"
      },
      "target": {
        "host": "example.org",
        "port": 80,
        "mode": "PLAIN"
      }
    }
  ],
  "ui": {
    "host": "127.0.0.1",
    "port": 8080,
    "enabled": true
  },
  "sessionsDir": "./sessions",
  "interceptMode": "NONE"
}
```

The same example in `YAML`:

```yaml
routes:
  - id: public-api
    listener:
      host: 127.0.0.1
      port: 9000
      mode: PLAIN
    target:
      host: jsonplaceholder.typicode.com
      port: 443
      mode: TLS
      sni: jsonplaceholder.typicode.com
      insecure: true
      rewriteHostHeader: true
  - id: legacy-http
    listener:
      host: 127.0.0.1
      port: 9001
      mode: PLAIN
    target:
      host: example.org
      port: 80
      mode: PLAIN
ui:
  host: 127.0.0.1
  port: 8080
  enabled: true
sessionsDir: ./sessions
interceptMode: NONE
```

With this file:

- `127.0.0.1:9000` points to the TLS target defined in `public-api`
- `127.0.0.1:9001` points to the plain target defined in `legacy-http`
- each session is tagged with its `routeId`

Start it:

```bash
java -jar target/tcpmon-tls-0.1.0-SNAPSHOT.jar --config tcpmon.json
```

Notes:

- when you use `routes[]`, that list fully defines the listeners and targets for the process
- the current CLI flags are still useful for single-route mode
- the UI shows `routeId` per session so you can tell which listener produced the traffic

## Boolean CLI flags

Boolean options support both styles:

```bash
--target-insecure
--target-insecure=true
```

The same applies to:

- `--ui-enabled`
- `--target-verify-hostname`
- `--rewrite-host-header`

## Certificates and TLS

TLS material can be loaded from:

- `PEM`
- `JKS`
- `PKCS12`

Typical usage:

- `cert + key PEM` for server/client certificates
- `truststore PEM/JKS/P12` to validate peers

### When to use `--target-sni`

It controls the hostname sent in the TLS handshake to the remote target.

It is useful when:

- the socket connects to an IP address but the certificate is issued for a hostname
- the remote server uses TLS virtual hosting
- you want to decouple `target-host` from the name announced in SNI

## `--target-insecure`

Disables remote certificate validation for outbound TLS.

It is intended for:

- local testing
- environments with internal or not-yet-trusted certificates

It should not be the default in production.

## `--rewrite-host-header`

Rewrites the HTTP `Host` header before sending the request to the remote target.

This is useful in flows like:

- `curl http://127.0.0.1:9000/...`
- remote HTTPS target expecting `Host: api.example.com`

Without this option, many backends will return `403`, `421`, or incorrect responses.

## Local UI

The UI shows:

- session list
- `routeId` per session
- inbound/outbound TLS metadata
- detected HTTP exchange list
- request and response per exchange
- headers and body when the payload can be parsed as HTTP
- raw events captured for the session

### Available actions

For `CLIENT_TO_TARGET` payloads:

- `Recapture request`
  - resends the request to the local listener
  - the request enters the proxy again
  - it is captured as a new session

- `Send direct`
  - resends the request directly to the current target

For intercepted payloads:

- `Forward original`
- `Edit/Forward`

## Persistence

Session history is stored under the directory configured in `sessionsDir`.

Current storage:

```text
sessions/
└── sessions.db
```

### What is stored

- session open/close metadata
- lifecycle events and errors
- TLS metadata
- request/response payloads
- event details used by the local UI

`pending payloads` remain in memory only and are not restored after restart.

## Interception

`--intercept-mode` supports:

- `NONE`
- `REQUEST`
- `RESPONSE`
- `BOTH`

When a direction is intercepted:

- the payload is not forwarded immediately
- it stays pending in memory
- you can forward it as-is or edit it from the UI

## Current limitations

- the UI HTTP parser supports `Content-Length`, `Transfer-Encoding: chunked`, `gzip`, `deflate`, and `br`
- it still does not interpret:
  - WebSocket
  - incremental HTTP streaming
- local recapture to a `TLS` listener with `client auth REQUIRE` does not present a client certificate yet
- the tool is optimized for local debugging, not high throughput
- the UI is focused on HTTP; generic TCP traffic falls back to raw view

## Development

Run tests:

```bash
mvn -q test
```

## Project structure

```text
src/main/java/com/cafeina/tcpmon/
├── config/     # JSON/YAML config loading
├── proxy/      # listeners, bridges, and HTTP rewriting
├── replay/     # resend to listener or target
├── session/    # session model and persistence
├── tls/        # TLS context construction
├── util/       # helpers
└── web/        # local API and UI
```

## Current status

The project already includes:

- a working build
- unit and integration tests
- a usable local UI
- a practical workflow for HTTP/HTTPS API debugging

If you continue development, the highest-value next steps would be:

- exchange import/export
- richer filtering and search by `routeId`
