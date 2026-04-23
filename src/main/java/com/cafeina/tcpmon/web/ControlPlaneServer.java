package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.UiConfig;
import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.proxy.RouteRegistry;
import com.cafeina.tcpmon.proxy.TcpMonProxy;
import com.cafeina.tcpmon.replay.ReplayDestination;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.session.SessionChangeEvent;
import com.cafeina.tcpmon.session.SessionChangeListener;
import com.cafeina.tcpmon.session.SessionEvent;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ControlPlaneServer implements AutoCloseable {
    private static final Pattern SAFE_HOSTNAME = Pattern.compile("^[a-zA-Z0-9.\\-_:\\[\\]%*]+$");
    static final String PRESERVE_SECRET = "__PRESERVE_SECRET__";
    private static final String AUTH_COOKIE_NAME = "tcpmon_auth";
    private static final int MAX_JSON_BODY_BYTES = 256 * 1024;
    private static final int MAX_REPLAY_PAYLOAD_BYTES = 1024 * 1024;
    private static final int MAX_SSE_CLIENTS = 32;
    private static final int HTTP_EXECUTOR_THREADS = Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2));
    private static final int HTTP_EXECUTOR_QUEUE_CAPACITY = 256;
    private static final String WEB_ROOT = "/web";
    private static final boolean METRICS_ENABLED = Boolean.getBoolean("tcpmon.metrics");
    private static final int MAX_REQUEST_LIMIT = 200;

    private final ProxyConfig proxyConfig;
    private final RouteRegistry registry;
    private final TcpMonProxy proxy;
    private final UiConfig config;
    private final SessionStore sessionStore;
    private final ReplayService replayService;
    private final ObjectMapper objectMapper = JsonSupport.objectMapper();
    private final CopyOnWriteArrayList<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final SessionChangeListener changeListener = this::broadcastSessionChange;
    private final ExecutorService httpExecutor = new ThreadPoolExecutor(
            HTTP_EXECUTOR_THREADS,
            HTTP_EXECUTOR_THREADS,
            30,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(HTTP_EXECUTOR_QUEUE_CAPACITY),
            new HttpWorkerThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
    private HttpServer server;

    public ControlPlaneServer(ProxyConfig proxyConfig, RouteRegistry registry, TcpMonProxy proxy, SessionStore sessionStore, ReplayService replayService) {
        this.proxyConfig = proxyConfig;
        this.registry = registry;
        this.proxy = proxy;
        this.config = proxyConfig.ui();
        this.sessionStore = sessionStore;
        this.replayService = replayService;
    }

    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.host(), config.port());
        if (config.tlsMaterial() != null) {
            try {
                HttpsServer httpsServer = HttpsServer.create(address, 0);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(buildSslContext(config.tlsMaterial())));
                server = httpsServer;
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to configure HTTPS for control plane", e);
            }
        } else {
            server = HttpServer.create(address, 0);
        }
        server.createContext("/assets/", this::handleAsset);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/requests", this::handleRequests);
        server.createContext("/api/request-facets", this::handleRequestFacets);
        server.createContext("/api/pending/", this::handlePending);
        server.createContext("/api/replay", this::handleReplay);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/runtime", this::handleRuntime);
        server.createContext("/api/routes", this::handleRoutes);
        server.createContext("/api/auth/session", this::handleAuthSession);
        server.setExecutor(httpExecutor);
        sessionStore.addChangeListener(changeListener);
        server.start();
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || path.isBlank()) {
            send(exchange, 200, "text/html; charset=utf-8", readWebText("/index.html"));
            return;
        }
        send(exchange, 404, "text/plain; charset=utf-8", "Not found");
    }

    private void handleAsset(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/assets/")) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String assetName = path.substring("/assets/".length());
        if (assetName.isBlank() || assetName.contains("..") || assetName.contains("\\")) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        byte[] body;
        try {
            body = readWebBytes("/" + assetName);
        } catch (IllegalArgumentException exception) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", assetContentType(assetName));
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(body);
        }
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        String path = exchange.getRequestURI().getPath();
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/sessions".equals(path)) {
            long t0 = System.currentTimeMillis();
            List<Map<String, Object>> sessions = sessionStore.listSessions();
            long t1 = System.currentTimeMillis();
            Map<String, Map<String, Object>> routeStats = sessionStore.listRouteStats();
            long t2 = System.currentTimeMillis();
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("sessions", sessions);
            payload.put("routeStats", routeStats);
            if (METRICS_ENABLED) {
                long t3 = System.currentTimeMillis();
                System.err.printf("/api/sessions sessions=%d dbMs=%d statsMs=%d jsonMs=%d%n",
                        sessions.size(), t1 - t0, t2 - t1, t3 - t2);
            }
            sendJson(exchange, 200, payload);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith("/api/sessions/")) {
            String remainder = path.substring("/api/sessions/".length());
            int exchangesMarker = remainder.indexOf("/exchanges/");
            if (exchangesMarker >= 0 && remainder.endsWith("/body")) {
                String sessionId = remainder.substring(0, exchangesMarker);
                String indexPart = remainder.substring(exchangesMarker + "/exchanges/".length(),
                        remainder.length() - "/body".length());
                int exchangeIndex;
                try {
                    exchangeIndex = Integer.parseInt(indexPart);
                } catch (NumberFormatException ignored) {
                    sendJson(exchange, 400, Map.of("error", "invalid exchange index"));
                    return;
                }
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String directionParam = params.getOrDefault("direction", "request");
                com.cafeina.tcpmon.Direction direction = "response".equalsIgnoreCase(directionParam)
                        ? com.cafeina.tcpmon.Direction.TARGET_TO_CLIENT
                        : com.cafeina.tcpmon.Direction.CLIENT_TO_TARGET;
                List<com.cafeina.tcpmon.session.SessionEvent> typedEvents =
                        sessionStore.listPayloadEventsForDirection(sessionId, direction);
                List<Map<String, Object>> messages = SessionPayloadAggregator.aggregateMessages(
                        typedEvents, direction, true);
                if (exchangeIndex >= messages.size()) {
                    sendJson(exchange, 404, Map.of("error", "exchange not found"));
                    return;
                }
                Map<String, Object> msg = messages.get(exchangeIndex);
                Map<String, Object> decoded = msg.get("decoded") instanceof Map<?, ?> d
                        ? (Map<String, Object>) d : Map.of();
                sendJson(exchange, 200, Map.of(
                        "bodyText", decoded.getOrDefault("bodyText", ""),
                        "bodyTruncated", false));
                return;
            }
            String sessionId = remainder;
            Map<String, Object> session = sessionStore.sessionDetails(sessionId);
            if (session == null) {
                sendJson(exchange, 404, Map.of("error", "session not found"));
                return;
            }
            sendJson(exchange, 200, enrichSession(session));
            return;
        }

        sendJson(exchange, 405, Map.of("error", "method not allowed"));
    }

    private void handleRequests(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String routeId = params.getOrDefault("routeId", "").trim();
        if (routeId.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "routeId is required"));
            return;
        }
        int limit = parseIntParam(params.getOrDefault("limit", "50"), 50, 1, MAX_REQUEST_LIMIT);
        String cursor = params.getOrDefault("cursor", null);
        String method = params.getOrDefault("method", null);
        String statusCode = params.getOrDefault("statusCode", null);
        String q = params.getOrDefault("q", null);
        long t0 = System.currentTimeMillis();
        Map<String, Object> result = sessionStore.listRequestRowsPaginated(routeId, limit, cursor, method, statusCode, q);
        long t1 = System.currentTimeMillis();
        if (METRICS_ENABLED) {
            Object rows = result.get("requests");
            int rowCount = rows instanceof List<?> list ? list.size() : 0;
            System.err.printf("/api/requests routeId=%s limit=%d dbMs=%d rows=%d hasMore=%s%n",
                    routeId, limit, t1 - t0, rowCount, result.get("hasMore"));
        }
        sendJson(exchange, 200, result);
    }

    private void handleRequestFacets(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String routeId = params.getOrDefault("routeId", "").trim();
        if (routeId.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "routeId is required"));
            return;
        }
        sendJson(exchange, 200, sessionStore.requestFacets(routeId));
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        if (sseClients.size() >= MAX_SSE_CLIENTS) {
            sendJson(exchange, 503, Map.of("error", "too many live update clients"));
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, 0);

        SseClient client = new SseClient(exchange);
        sseClients.add(client);
        client.offer("connected", Map.of("status", "ok"));
        try {
            client.stream();
        } finally {
            sseClients.remove(client);
            client.close();
        }
    }

    private void handlePending(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().endsWith("/forward")) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String pendingId = path.substring("/api/pending/".length(), path.length() - "/forward".length());
        try {
            JsonNode body = readJsonBody(exchange);
            byte[] replacement = null;
            if (body.hasNonNull("http")) {
                replacement = HttpMessageEditor.buildHttpMessage(body.path("http"));
            } else if (body.hasNonNull("content")) {
                replacement = decodePayload(body.path("encoding").asText("utf8"), body.path("content").asText(""));
            }
            enforcePayloadLimit(replacement);
            boolean released = sessionStore.releasePending(pendingId, replacement);
            if (!released) {
                sendJson(exchange, 404, Map.of("error", "pending payload not found"));
                return;
            }
            sendJson(exchange, 200, Map.of("status", "released", "pendingId", pendingId));
        } catch (RequestTooLargeException exception) {
            sendJson(exchange, 413, Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        }
    }

    private void handleReplay(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        try {
            JsonNode body = readJsonBody(exchange);
            ReplayDestination destination = ReplayDestination.fromString(body.path("destination").asText("listener"));
            String routeId = body.path("routeId").asText();
            if (body.hasNonNull("base64")) {
                if (routeId == null || routeId.isBlank()) {
                    sendJson(exchange, 400, Map.of("error", "routeId is required when replaying raw payloads"));
                    return;
                }
                byte[] payload = Base64.getDecoder().decode(body.path("base64").asText());
                enforcePayloadLimit(payload);
                Map<String, Object> result = replayService.replay(payload, routeId, destination);
                sendJson(exchange, 200, result);
                return;
            }

            if (body.hasNonNull("sessionId") && body.hasNonNull("exchangeIndex")) {
                String sessionId = body.path("sessionId").asText();
                int exchangeIndex = body.path("exchangeIndex").asInt(0);
                com.cafeina.tcpmon.Direction direction = com.cafeina.tcpmon.Direction.CLIENT_TO_TARGET;
                Map<String, Object> session = sessionStore.sessionDetails(sessionId);
                if (session == null) {
                    sendJson(exchange, 404, Map.of("error", "session not found"));
                    return;
                }
                String resolvedRouteId = routeId != null && !routeId.isBlank()
                        ? routeId : sessionStore.routeIdForSession(sessionId);
                if (resolvedRouteId == null) {
                    sendJson(exchange, 404, Map.of("error", "session route not found"));
                    return;
                }
                Object eventsValue = session.get("events");
                if (!(eventsValue instanceof List<?> rawEvents)) {
                    sendJson(exchange, 404, Map.of("error", "no events"));
                    return;
                }
                List<com.cafeina.tcpmon.session.SessionEvent> typedEvents = rawEvents.stream()
                        .filter(e -> e instanceof com.cafeina.tcpmon.session.SessionEvent)
                        .map(e -> (com.cafeina.tcpmon.session.SessionEvent) e)
                        .toList();
                List<Map<String, Object>> messages = SessionPayloadAggregator.aggregateMessages(
                        typedEvents, direction, true);
                if (exchangeIndex >= messages.size()) {
                    sendJson(exchange, 404, Map.of("error", "exchange not found"));
                    return;
                }
                Object base64Obj = messages.get(exchangeIndex).get("base64");
                if (!(base64Obj instanceof String base64Str)) {
                    sendJson(exchange, 404, Map.of("error", "exchange payload not available"));
                    return;
                }
                byte[] payload = Base64.getDecoder().decode(base64Str);
                enforcePayloadLimit(payload);
                Map<String, Object> result = replayService.replay(payload, resolvedRouteId, destination);
                sendJson(exchange, 200, result);
                return;
            }

            String sessionId = body.path("sessionId").asText();
            String eventId = body.path("eventId").asText();
            SessionEvent event = sessionStore.findEvent(sessionId, eventId);
            if (event == null) {
                sendJson(exchange, 404, Map.of("error", "event not found"));
                return;
            }
            if (!isReplayable(event)) {
                sendJson(exchange, 400, Map.of("error", "only client-to-target payloads can be replayed"));
                return;
            }
            routeId = sessionStore.routeIdForSession(sessionId);
            if (routeId == null) {
                sendJson(exchange, 404, Map.of("error", "session route not found"));
                return;
            }
            String base64 = event.details().get("base64").toString();
            byte[] payload = Base64.getDecoder().decode(base64);
            enforcePayloadLimit(payload);
            Map<String, Object> result = replayService.replay(payload, routeId, destination);
            sendJson(exchange, 200, result);
        } catch (RequestTooLargeException exception) {
            sendJson(exchange, 413, Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            sendJson(exchange, 502, Map.of("error", sanitizeExceptionMessage(exception)));
        }
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, buildConfigPayload());
    }

    private void handleRuntime(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, buildRuntimePayload());
    }

    private void handleRoutes(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) return;
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/api/routes".equals(path)) {
            handleCreateRoute(exchange);
        } else if ("PUT".equals(method) && path.startsWith("/api/routes/")) {
            String id = path.substring("/api/routes/".length());
            handleUpdateRoute(exchange, id);
        } else if ("DELETE".equals(method) && path.startsWith("/api/routes/")) {
            String id = path.substring("/api/routes/".length());
            handleDeleteRoute(exchange, id);
        } else {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
        }
    }

    private void handleAuthSession(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                JsonNode body = readJsonBody(exchange);
                String submittedToken = body.path("token").asText("").trim();
                String configuredToken = config.apiToken();
                if (configuredToken == null || configuredToken.isBlank()) {
                    sendJson(exchange, 400, Map.of("error", "API token auth is not enabled"));
                    return;
                }
                if (!configuredToken.equals(submittedToken)) {
                    sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                    return;
                }
                exchange.getResponseHeaders().add("Set-Cookie", buildAuthCookieHeader(configuredToken, config.tlsMaterial() != null));
                sendJson(exchange, 200, Map.of("status", "authenticated"));
                return;
            } catch (RequestTooLargeException exception) {
                sendJson(exchange, 413, Map.of("error", exception.getMessage()));
                return;
            }
        }

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Set-Cookie", clearAuthCookieHeader(config.tlsMaterial() != null));
            sendJson(exchange, 200, Map.of("status", "cleared"));
            return;
        }

        sendJson(exchange, 405, Map.of("error", "method not allowed"));
    }

    private void handleCreateRoute(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = readJsonBody(exchange);
        } catch (RequestTooLargeException e) {
            sendJson(exchange, 413, Map.of("error", e.getMessage()));
            return;
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "invalid JSON body"));
            return;
        }

        RouteConfig route;
        try {
            route = parseRouteFromJson(body);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
            return;
        }

        boolean added = registry.add(route);
        if (!added) {
            sendJson(exchange, 409, Map.of("error", "Route with ID already exists: " + route.id()));
            return;
        }

        try {
            proxy.bindRoute(route);
        } catch (Exception e) {
            registry.remove(route.id());
            sendJson(exchange, 500, Map.of("error", "Failed to bind route: " + e.getMessage()));
            return;
        }

        sendJson(exchange, 201, buildConfigPayload());
    }

    private void handleUpdateRoute(HttpExchange exchange, String id) throws IOException {
        RouteConfig original = registry.findById(id).orElse(null);
        if (original == null) {
            sendJson(exchange, 404, Map.of("error", "Route not found: " + id));
            return;
        }

        JsonNode body;
        try {
            body = readJsonBody(exchange);
        } catch (RequestTooLargeException e) {
            sendJson(exchange, 413, Map.of("error", e.getMessage()));
            return;
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "invalid JSON body"));
            return;
        }

        RouteConfig updated;
        try {
            updated = parseRouteFromJson(body, id);
            updated = mergeTlsSecrets(original, updated);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
            return;
        }

        proxy.unbindRoute(id);
        registry.replace(id, updated);

        try {
            proxy.bindRoute(updated);
        } catch (Exception e) {
            try { proxy.bindRoute(original); } catch (Exception ignored) {}
            registry.replace(id, original);
            sendJson(exchange, 500, Map.of("error", "Failed to bind updated route: " + e.getMessage()));
            return;
        }

        sendJson(exchange, 200, buildConfigPayload());
    }

    private void handleDeleteRoute(HttpExchange exchange, String id) throws IOException {
        if (registry.findById(id).isEmpty()) {
            sendJson(exchange, 404, Map.of("error", "Route not found: " + id));
            return;
        }

        proxy.unbindRoute(id);
        registry.remove(id);
        sendJson(exchange, 200, buildConfigPayload());
    }

    private RouteConfig parseRouteFromJson(JsonNode body) {
        return parseRouteFromJson(body, null);
    }

    private RouteConfig parseRouteFromJson(JsonNode body, String fixedId) {
        String id = fixedId != null ? fixedId : body.path("id").asText("").trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("Route id is required");
        }
        if (!id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Route id must match [a-zA-Z0-9_-]+");
        }

        JsonNode listenerNode = body.path("listener");
        String listenerHost = listenerNode.path("host").asText("0.0.0.0");
        validateHostname(listenerHost, "Listener host");
        int listenerPort = listenerNode.path("port").asInt(0);
        if (listenerPort < 1 || listenerPort > 65535) {
            throw new IllegalArgumentException("Listener port must be between 1 and 65535");
        }
        TransportMode listenerTransport = parseTransport(listenerNode.path("transport").asText("PLAIN"));
        ClientAuthMode clientAuth = parseClientAuth(listenerNode.path("clientAuth").asText("NONE"));

        JsonNode targetNode = body.path("target");
        String targetHost = targetNode.path("host").asText("").trim();
        if (targetHost.isBlank()) {
            throw new IllegalArgumentException("Target host is required");
        }
        validateHostname(targetHost, "Target host");
        int targetPort = targetNode.path("port").asInt(0);
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        TransportMode targetTransport = parseTransport(targetNode.path("transport").asText("PLAIN"));
        String sniHost = targetNode.path("sniHost").isNull() ? null : targetNode.path("sniHost").asText(null);
        if (sniHost != null && !sniHost.isBlank()) {
            validateHostname(sniHost, "SNI host");
        }
        boolean insecureTrustAll = targetNode.path("insecureTrustAll").asBoolean(false);
        boolean verifyHostname = targetNode.path("verifyHostname").asBoolean(true);
        boolean rewriteHostHeader = targetNode.path("rewriteHostHeader").asBoolean(false);

        TlsMaterial listenerTls = parseTlsMaterial(listenerNode);
        TlsMaterial targetTls = parseTlsMaterial(targetNode);

        ListenerConfig listener = new ListenerConfig(listenerHost, listenerPort, listenerTransport, clientAuth, listenerTls);
        TargetConfig target = new TargetConfig(targetHost, targetPort, targetTransport, sniHost, insecureTrustAll, verifyHostname, rewriteHostHeader, targetTls);
        return new RouteConfig(id, listener, target);
    }

    private static void validateHostname(String hostname, String fieldName) {
        if (hostname.length() > 253) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of 253 characters");
        }
        if (!SAFE_HOSTNAME.matcher(hostname).matches()) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters: " + hostname);
        }
    }

    private static TlsMaterial parseTlsMaterial(JsonNode node) {
        String cert = nullIfBlank(node.path("tlsCert").asText(null));
        String key = nullIfBlank(node.path("tlsKey").asText(null));
        String keystore = nullIfBlank(node.path("tlsKeystore").asText(null));
        String keystorePassword = nullIfBlank(node.path("tlsKeystorePassword").asText(null));
        String keystoreType = nullIfBlank(node.path("tlsKeystoreType").asText(null));
        String truststore = nullIfBlank(node.path("tlsTruststore").asText(null));
        String truststorePassword = nullIfBlank(node.path("tlsTruststorePassword").asText(null));
        String truststoreType = nullIfBlank(node.path("tlsTruststoreType").asText(null));
        if (cert == null && key == null && keystore == null && truststore == null
                && keystorePassword == null && truststorePassword == null) {
            return null;
        }
        return new TlsMaterial(
                cert != null ? java.nio.file.Path.of(cert) : null,
                key != null ? java.nio.file.Path.of(key) : null,
                keystore != null ? java.nio.file.Path.of(keystore) : null,
                keystorePassword,
                truststore != null ? java.nio.file.Path.of(truststore) : null,
                truststorePassword,
                keystoreType != null ? keystoreType : "PKCS12",
                truststoreType != null ? truststoreType : "PKCS12");
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private TransportMode parseTransport(String value) {
        try {
            return TransportMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transport mode: " + value + " (expected PLAIN or TLS)");
        }
    }

    private ClientAuthMode parseClientAuth(String value) {
        try {
            return ClientAuthMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid clientAuth mode: " + value);
        }
    }

    private Map<String, Object> buildConfigPayload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interceptMode", proxyConfig.interceptMode().name());
        List<Map<String, Object>> routes = new ArrayList<>();
        for (var route : registry.routes()) {
            Map<String, Object> routeMap = new LinkedHashMap<>();
            routeMap.put("id", route.id());
            Map<String, Object> listener = new LinkedHashMap<>();
            listener.put("host", route.listener().host());
            listener.put("port", route.listener().port());
            listener.put("transport", route.listener().transportMode().name());
            listener.put("clientAuth", route.listener().clientAuthMode().name());
            putTlsMaterial(listener, route.listener().tlsMaterial());
            routeMap.put("listener", listener);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("host", route.target().host());
            target.put("port", route.target().port());
            target.put("transport", route.target().transportMode().name());
            target.put("sniHost", route.target().sniHost());
            target.put("insecureTrustAll", route.target().insecureTrustAll());
            target.put("verifyHostname", route.target().verifyHostname());
            target.put("rewriteHostHeader", route.target().rewriteHostHeader());
            putTlsMaterial(target, route.target().tlsMaterial());
            routeMap.put("target", target);
            routes.add(routeMap);
        }
        result.put("routes", routes);
        return result;
    }

    private Map<String, Object> buildRuntimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("uiEnabled", config.enabled());
        payload.put("uiTlsEnabled", config.tlsMaterial() != null);
        payload.put("apiTokenEnabled", config.apiToken() != null && !config.apiToken().isBlank());
        payload.put("interceptMode", proxyConfig.interceptMode().name());
        payload.put("routeCount", registry.routes().size());
        payload.put("sessionCount", sessionStore.listSessions().size());
        payload.put("liveUpdateClients", sseClients.size());
        payload.put("liveUpdateClientLimit", MAX_SSE_CLIENTS);
        payload.put("sessionsDir", proxyConfig.sessionsDir().toAbsolutePath().toString());
        return payload;
    }

    private static void putTlsMaterial(Map<String, Object> map, TlsMaterial tls) {
        if (tls == null) return;
        if (tls.certificateFile() != null) map.put("tlsCert", tls.certificateFile().toString());
        if (tls.privateKeyFile() != null) map.put("tlsKey", tls.privateKeyFile().toString());
        if (tls.keyStoreFile() != null) map.put("tlsKeystore", tls.keyStoreFile().toString());
        map.put("tlsKeystorePasswordConfigured", tls.keyStorePassword() != null && !tls.keyStorePassword().isBlank());
        if (tls.keyStoreType() != null) map.put("tlsKeystoreType", tls.keyStoreType());
        if (tls.trustStoreFile() != null) map.put("tlsTruststore", tls.trustStoreFile().toString());
        map.put("tlsTruststorePasswordConfigured", tls.trustStorePassword() != null && !tls.trustStorePassword().isBlank());
        if (tls.trustStoreType() != null) map.put("tlsTruststoreType", tls.trustStoreType());
    }

    static RouteConfig mergeTlsSecrets(RouteConfig original, RouteConfig updated) {
        return new RouteConfig(
                updated.id(),
                new ListenerConfig(
                        updated.listener().host(),
                        updated.listener().port(),
                        updated.listener().transportMode(),
                        updated.listener().clientAuthMode(),
                        mergeTlsMaterial(original.listener().tlsMaterial(), updated.listener().tlsMaterial())),
                new TargetConfig(
                        updated.target().host(),
                        updated.target().port(),
                        updated.target().transportMode(),
                        updated.target().sniHost(),
                        updated.target().insecureTrustAll(),
                        updated.target().verifyHostname(),
                        updated.target().rewriteHostHeader(),
                        mergeTlsMaterial(original.target().tlsMaterial(), updated.target().tlsMaterial())));
    }

    static TlsMaterial mergeTlsMaterial(TlsMaterial original, TlsMaterial updated) {
        if (updated == null) {
            return null;
        }
        String keyStorePassword = updated.keyStorePassword();
        if (PRESERVE_SECRET.equals(keyStorePassword)) {
            keyStorePassword = original == null ? null : original.keyStorePassword();
        }
        String trustStorePassword = updated.trustStorePassword();
        if (PRESERVE_SECRET.equals(trustStorePassword)) {
            trustStorePassword = original == null ? null : original.trustStorePassword();
        }
        return new TlsMaterial(
                updated.certificateFile(),
                updated.privateKeyFile(),
                updated.keyStoreFile(),
                keyStorePassword,
                updated.trustStoreFile(),
                trustStorePassword,
                updated.keyStoreType(),
                updated.trustStoreType());
    }

    private JsonNode readJsonBody(HttpExchange exchange) throws IOException {
        byte[] requestBody = readRequestBody(exchange, MAX_JSON_BODY_BYTES);
        if (requestBody.length == 0) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(requestBody);
    }

    private static byte[] readRequestBody(HttpExchange exchange, int maxBytes) throws IOException {
        try (InputStream stream = exchange.getRequestBody()) {
            byte[] buffer = stream.readNBytes(maxBytes + 1);
            if (buffer.length > maxBytes) {
                throw new RequestTooLargeException("request body exceeds " + maxBytes + " bytes");
            }
            return buffer;
        }
    }

    private byte[] decodePayload(String encoding, String content) {
        return switch (encoding.toLowerCase()) {
            case "base64" -> Base64.getDecoder().decode(content);
            case "hex" -> HexFormat.of().parseHex(content.replaceAll("\\s+", ""));
            default -> content.getBytes(StandardCharsets.UTF_8);
        };
    }

    private static void enforcePayloadLimit(byte[] payload) throws RequestTooLargeException {
        if (payload != null && payload.length > MAX_REPLAY_PAYLOAD_BYTES) {
            throw new RequestTooLargeException("payload exceeds " + MAX_REPLAY_PAYLOAD_BYTES + " bytes");
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private static int parseIntParam(String value, int defaultValue, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean requireAuth(HttpExchange exchange) throws IOException {
        String token = config.apiToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.equals("Bearer " + token)) {
            return true;
        }
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        String cookieToken = readCookie(cookieHeader, AUTH_COOKIE_NAME);
        if (token.equals(cookieToken)) {
            return true;
        }
        sendJson(exchange, 401, Map.of("error", "Unauthorized"));
        return false;
    }

    static String readCookie(String header, String name) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String cookie : header.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equals(name)) {
                return pair[1].trim();
            }
        }
        return null;
    }

    static String buildAuthCookieHeader(String token, boolean secure) {
        StringBuilder builder = new StringBuilder();
        builder.append(AUTH_COOKIE_NAME)
                .append('=')
                .append(token)
                .append("; Path=/; HttpOnly; SameSite=Strict");
        if (secure) {
            builder.append("; Secure");
        }
        return builder.toString();
    }

    static String clearAuthCookieHeader(boolean secure) {
        StringBuilder builder = new StringBuilder();
        builder.append(AUTH_COOKIE_NAME)
                .append("=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
        if (secure) {
            builder.append("; Secure");
        }
        return builder.toString();
    }

    static String sanitizeExceptionMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", objectMapper.writeValueAsString(payload));
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean useGzip = acceptEncoding != null && acceptEncoding.contains("gzip") && bytes.length > 512;
        if (useGzip) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(bytes.length / 2);
            try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
                gz.write(bytes);
            }
            bytes = buf.toByteArray();
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        if (useGzip) {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    static String readWebText(String resourcePath) {
        return new String(readWebBytes(resourcePath), StandardCharsets.UTF_8);
    }

    static byte[] readWebBytes(String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream stream = ControlPlaneServer.class.getResourceAsStream(WEB_ROOT + normalized)) {
            if (stream == null) {
                throw new IllegalArgumentException("Web resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read web resource " + resourcePath, exception);
        }
    }

    static String assetContentType(String assetName) {
        if (assetName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (assetName.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (assetName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (assetName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (assetName.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (assetName.endsWith(".woff")) {
            return "font/woff";
        }
        if (assetName.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (assetName.endsWith(".otf")) {
            return "font/otf";
        }
        return "application/octet-stream";
    }

    private void broadcastSessionChange(SessionChangeEvent event) {
        for (SseClient client : sseClients) {
            client.offer(event.type(), event);
        }
    }

    private Map<String, Object> enrichSession(Map<String, Object> session) {
        Map<String, Object> enriched = new LinkedHashMap<>(session);
        Object eventsValue = session.get("events");
        if (!(eventsValue instanceof List<?> rawEvents)) {
            return enriched;
        }

        List<Object> events = new ArrayList<>();
        List<SessionEvent> typedEvents = new ArrayList<>();
        for (Object rawEvent : rawEvents) {
            if (rawEvent instanceof SessionEvent event) {
                typedEvents.add(event);
                Map<String, Object> inspected = PayloadInspector.inspect(event);
                events.add(inspected);
            } else {
                events.add(rawEvent);
            }
        }
        List<Map<String, Object>> requests = SessionPayloadAggregator.aggregateMessages(typedEvents, com.cafeina.tcpmon.Direction.CLIENT_TO_TARGET);
        List<Map<String, Object>> responses = SessionPayloadAggregator.aggregateMessages(typedEvents, com.cafeina.tcpmon.Direction.TARGET_TO_CLIENT);
        List<Map<String, Object>> exchanges = new ArrayList<>();
        int max = Math.max(requests.size(), responses.size());
        for (int index = 0; index < max; index++) {
            Map<String, Object> exchange = new LinkedHashMap<>();
            exchange.put("index", index);
            exchange.put("request", index < requests.size() ? stripPayloadBase64(requests.get(index)) : null);
            exchange.put("response", index < responses.size() ? stripPayloadBase64(responses.get(index)) : null);
            exchanges.add(exchange);
        }
        enriched.put("events", events);
        enriched.put("requests", requests.stream().map(ControlPlaneServer::stripPayloadBase64).toList());
        enriched.put("responses", responses.stream().map(ControlPlaneServer::stripPayloadBase64).toList());
        enriched.put("exchanges", exchanges);
        enriched.put("latestRequest", requests.isEmpty() ? null : stripPayloadBase64(requests.getLast()));
        enriched.put("latestResponse", responses.isEmpty() ? null : stripPayloadBase64(responses.getLast()));
        return enriched;
    }

    private static Map<String, Object> stripPayloadBase64(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("base64")) {
            return payload;
        }
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.remove("base64");
        return copy;
    }

    private List<Map<String, Object>> summarizeRequests() {
        return sessionStore.listRequestRows();
    }

    private Map<String, Object> summarizeSession(Map<String, Object> summary) {
        String sessionId = String.valueOf(summary.get("sessionId"));
        List<Map<String, Object>> rows = sessionStore.listRequestRows().stream()
                .filter(row -> sessionId.equals(String.valueOf(row.get("sessionId"))))
                .toList();
        return summarizeSession(summary, rows);
    }

    private Map<String, Object> summarizeSession(Map<String, Object> summary, List<Map<String, Object>> requestRows) {
        Map<String, Object> enriched = new LinkedHashMap<>(summary);
        Map<String, Object> latestRequest = latestRequestRow(requestRows);
        boolean live = isSessionLive(summary.get("status"), requestRows);
        enriched.put("requestMethod", latestRequest == null ? "" : latestRequest.getOrDefault("requestMethod", ""));
        enriched.put("requestPath", latestRequest == null ? "" : latestRequest.getOrDefault("requestPath", ""));
        enriched.put("responseStatusCode", latestRequest == null ? "" : latestRequest.getOrDefault("responseStatusCode", ""));
        enriched.put("live", live);
        Object startedAt = summary.get("startedAt");
        Object endedAt = summary.get("endedAt");
        if (startedAt instanceof Instant start) {
            Instant effectiveEnd = endedAt instanceof Instant end ? end : responseTimestamp(latestRequest);
            if (effectiveEnd != null && !effectiveEnd.isBefore(start)) {
                enriched.put("durationMs", Duration.between(start, effectiveEnd).toMillis());
            }
        }
        if (latestRequest != null && latestRequest.get("responseSizeBytes") instanceof Number number) {
            enriched.put("responseSizeBytes", number.longValue());
        }
        return enriched;
    }

    private static Map<String, Object> latestRequestRow(List<Map<String, Object>> requestRows) {
        Map<String, Object> latest = null;
        int latestExchangeIndex = -1;
        for (Map<String, Object> requestRow : requestRows) {
            int exchangeIndex = requestRow.get("exchangeIndex") instanceof Number number ? number.intValue() : 0;
            if (latest == null || exchangeIndex > latestExchangeIndex) {
                latest = requestRow;
                latestExchangeIndex = exchangeIndex;
            }
        }
        return latest;
    }

    private static Map<String, List<Map<String, Object>>> groupRequestsBySession(List<Map<String, Object>> requests) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> request : requests) {
            grouped.computeIfAbsent(String.valueOf(request.get("sessionId")), ignored -> new ArrayList<>()).add(request);
        }
        return grouped;
    }

    private static boolean isSessionLive(Object statusValue, List<Map<String, Object>> requestRows) {
        String status = statusValue == null ? "" : statusValue.toString();
        if (!"OPEN".equalsIgnoreCase(status)) {
            return false;
        }
        if (requestRows.isEmpty()) {
            return true;
        }
        return requestRows.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("live")));
    }

    private static Instant responseTimestamp(Map<String, Object> requestRow) {
        if (requestRow == null) {
            return null;
        }
        Object endedAt = requestRow.get("endedAt");
        return endedAt instanceof Instant instant ? instant : null;
    }

    static boolean isReplayable(SessionEvent event) {
        return "PAYLOAD".equals(event.type()) && event.direction() == Direction.CLIENT_TO_TARGET;
    }

    private static SSLContext buildSslContext(TlsMaterial material) throws GeneralSecurityException, IOException {
        if (material.keyStoreFile() == null) {
            throw new IllegalArgumentException("UI HTTPS requires a keystore (--ui-tls-keystore)");
        }
        String type = material.keyStoreType() != null ? material.keyStoreType() : "PKCS12";
        char[] password = material.keyStorePassword() != null
                ? material.keyStorePassword().toCharArray()
                : new char[0];
        KeyStore keyStore = KeyStore.getInstance(type);
        try (var stream = Files.newInputStream(material.keyStoreFile())) {
            keyStore.load(stream, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    @Override
    public void close() {
        sessionStore.removeChangeListener(changeListener);
        for (SseClient client : sseClients) {
            client.close();
        }
        sseClients.clear();
        if (server != null) {
            server.stop(0);
        }
        httpExecutor.shutdownNow();
    }

    private final class SseClient implements AutoCloseable {
        private final HttpExchange exchange;
        private final Writer writer;
        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        private SseClient(HttpExchange exchange) throws IOException {
            this.exchange = exchange;
            this.writer = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8);
        }

        private void offer(String eventName, Object payload) {
            if (closed) {
                return;
            }
            try {
                queue.offer("event: " + eventName + "\n" + "data: " + objectMapper.writeValueAsString(payload) + "\n\n");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void stream() throws IOException {
            while (!closed) {
                try {
                    String message = queue.poll(15, TimeUnit.SECONDS);
                    if (message == null) {
                        writer.write(": ping\n\n");
                    } else {
                        writer.write(message);
                    }
                    writer.flush();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (UncheckedIOException exception) {
                    throw exception.getCause();
                }
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                writer.close();
            } catch (IOException ignored) {
            } finally {
                exchange.close();
            }
        }
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException(String message) {
            super(message);
        }
    }

    private static final class HttpWorkerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Thread.ofPlatform().name("control-plane-http-", 0).unstarted(runnable);
            thread.setDaemon(true);
            return thread;
        }
    }
}
