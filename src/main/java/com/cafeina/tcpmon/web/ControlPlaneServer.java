package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class ControlPlaneServer implements AutoCloseable {
    private final ProxyConfig proxyConfig;
    private final RouteRegistry registry;
    private final TcpMonProxy proxy;
    private final UiConfig config;
    private final SessionStore sessionStore;
    private final ReplayService replayService;
    private final ObjectMapper objectMapper = JsonSupport.objectMapper();
    private final CopyOnWriteArrayList<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final SessionChangeListener changeListener = this::broadcastSessionChange;
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
        server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/pending/", this::handlePending);
        server.createContext("/api/replay", this::handleReplay);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/routes", this::handleRoutes);
        server.setExecutor(Executors.newCachedThreadPool());
        sessionStore.addChangeListener(changeListener);
        server.start();
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method not allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", WebAssets.indexHtml());
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/sessions".equals(path)) {
            sendJson(exchange, 200, Map.of("sessions", summarizeSessions()));
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith("/api/sessions/")) {
            String sessionId = path.substring("/api/sessions/".length());
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

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
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
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().endsWith("/forward")) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String pendingId = path.substring("/api/pending/".length(), path.length() - "/forward".length());
        JsonNode body = readJsonBody(exchange);
        byte[] replacement = null;
        if (body.hasNonNull("http")) {
            replacement = HttpMessageEditor.buildHttpMessage(body.path("http"));
        } else if (body.hasNonNull("content")) {
            replacement = decodePayload(body.path("encoding").asText("utf8"), body.path("content").asText(""));
        }
        boolean released = sessionStore.releasePending(pendingId, replacement);
        if (!released) {
            sendJson(exchange, 404, Map.of("error", "pending payload not found"));
            return;
        }
        sendJson(exchange, 200, Map.of("status", "released", "pendingId", pendingId));
    }

    private void handleReplay(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        JsonNode body = readJsonBody(exchange);
        try {
            ReplayDestination destination = ReplayDestination.fromString(body.path("destination").asText("listener"));
            String routeId = body.path("routeId").asText();
            if (body.hasNonNull("base64")) {
                if (routeId == null || routeId.isBlank()) {
                    sendJson(exchange, 400, Map.of("error", "routeId is required when replaying raw payloads"));
                    return;
                }
                Map<String, Object> result = replayService.replay(java.util.Base64.getDecoder().decode(body.path("base64").asText()), routeId, destination);
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
            Map<String, Object> result = replayService.replay(java.util.Base64.getDecoder().decode(base64), routeId, destination);
            sendJson(exchange, 200, result);
        } catch (Exception exception) {
            sendJson(exchange, 500, Map.of("error", exception.toString()));
        }
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, buildConfigPayload());
    }

    private void handleRoutes(HttpExchange exchange) throws IOException {
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

    private void handleCreateRoute(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = readJsonBody(exchange);
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

        if (route.listener().transportMode() == TransportMode.TLS || route.target().transportMode() == TransportMode.TLS) {
            sendJson(exchange, 400, Map.of("error", "TLS transport requires TLS material configured in the config file; use PLAIN for routes created via UI"));
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
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "invalid JSON body"));
            return;
        }

        RouteConfig updated;
        try {
            updated = parseRouteFromJson(body, id);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
            return;
        }

        if (updated.listener().transportMode() == TransportMode.TLS || updated.target().transportMode() == TransportMode.TLS) {
            sendJson(exchange, 400, Map.of("error", "TLS transport requires TLS material configured in the config file; use PLAIN for routes created via UI"));
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
        int targetPort = targetNode.path("port").asInt(0);
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        TransportMode targetTransport = parseTransport(targetNode.path("transport").asText("PLAIN"));
        String sniHost = targetNode.path("sniHost").isNull() ? null : targetNode.path("sniHost").asText(null);
        boolean insecureTrustAll = targetNode.path("insecureTrustAll").asBoolean(false);
        boolean verifyHostname = targetNode.path("verifyHostname").asBoolean(true);
        boolean rewriteHostHeader = targetNode.path("rewriteHostHeader").asBoolean(false);

        ListenerConfig listener = new ListenerConfig(listenerHost, listenerPort, listenerTransport, clientAuth, null);
        TargetConfig target = new TargetConfig(targetHost, targetPort, targetTransport, sniHost, insecureTrustAll, verifyHostname, rewriteHostHeader, null);
        return new RouteConfig(id, listener, target);
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
            routeMap.put("listener", listener);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("host", route.target().host());
            target.put("port", route.target().port());
            target.put("transport", route.target().transportMode().name());
            target.put("insecureTrustAll", route.target().insecureTrustAll());
            target.put("verifyHostname", route.target().verifyHostname());
            target.put("rewriteHostHeader", route.target().rewriteHostHeader());
            routeMap.put("target", target);
            routes.add(routeMap);
        }
        result.put("routes", routes);
        return result;
    }

    private JsonNode readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream stream = exchange.getRequestBody()) {
            return objectMapper.readTree(stream);
        }
    }

    private byte[] decodePayload(String encoding, String content) {
        return switch (encoding.toLowerCase()) {
            case "base64" -> java.util.Base64.getDecoder().decode(content);
            case "hex" -> HexFormat.of().parseHex(content.replaceAll("\\s+", ""));
            default -> content.getBytes(StandardCharsets.UTF_8);
        };
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", objectMapper.writeValueAsString(payload));
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
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
            exchange.put("request", index < requests.size() ? requests.get(index) : null);
            exchange.put("response", index < responses.size() ? responses.get(index) : null);
            exchanges.add(exchange);
        }
        enriched.put("events", events);
        enriched.put("requests", requests);
        enriched.put("responses", responses);
        enriched.put("exchanges", exchanges);
        enriched.put("latestRequest", requests.isEmpty() ? null : requests.getLast());
        enriched.put("latestResponse", responses.isEmpty() ? null : responses.getLast());
        return enriched;
    }

    private List<Map<String, Object>> summarizeSessions() {
        return sessionStore.listSessions().stream()
                .map(this::summarizeSession)
                .toList();
    }

    private Map<String, Object> summarizeSession(Map<String, Object> summary) {
        Map<String, Object> enriched = new LinkedHashMap<>(summary);
        String sessionId = String.valueOf(summary.get("sessionId"));
        Map<String, Object> details = sessionStore.sessionDetails(sessionId);
        if (details == null) {
            return enriched;
        }

        Object eventsValue = details.get("events");
        if (!(eventsValue instanceof List<?> rawEvents)) {
            return enriched;
        }

        List<SessionEvent> typedEvents = new ArrayList<>();
        for (Object rawEvent : rawEvents) {
            if (rawEvent instanceof SessionEvent event) {
                typedEvents.add(event);
            }
        }

        List<Map<String, Object>> requests = SessionPayloadAggregator.aggregateMessages(typedEvents, Direction.CLIENT_TO_TARGET);
        List<Map<String, Object>> responses = SessionPayloadAggregator.aggregateMessages(typedEvents, Direction.TARGET_TO_CLIENT);

        Map<String, Object> latestRequest = requests.isEmpty() ? null : requests.getLast();
        Map<String, Object> latestResponse = responses.isEmpty() ? null : responses.getLast();
        enriched.put("requestMethod", extractRequestMethod(latestRequest));
        enriched.put("requestPath", extractRequestPath(latestRequest));
        enriched.put("responseStatusCode", extractResponseStatusCode(latestResponse));
        Object startedAt = details.get("startedAt");
        Object endedAt = details.get("endedAt");
        if (startedAt instanceof Instant start && endedAt instanceof Instant end) {
            enriched.put("durationMs", Duration.between(start, end).toMillis());
        }
        if (latestResponse != null) {
            Object size = latestResponse.get("size");
            if (size instanceof Number n) {
                enriched.put("responseSizeBytes", n.longValue());
            }
        }
        return enriched;
    }

    private static String extractRequestMethod(Map<String, Object> request) {
        if (request == null) {
            return "";
        }
        Object decodedValue = request.get("decoded");
        if (!(decodedValue instanceof Map<?, ?> decoded)) {
            return "";
        }
        Object requestValue = decoded.get("request");
        if (!(requestValue instanceof Map<?, ?> requestMeta)) {
            return "";
        }
        Object method = requestMeta.get("method");
        return method == null ? "" : method.toString();
    }

    private static String extractRequestPath(Map<String, Object> request) {
        if (request == null) {
            return "";
        }
        Object decodedValue = request.get("decoded");
        if (!(decodedValue instanceof Map<?, ?> decoded)) {
            return "";
        }
        Object requestValue = decoded.get("request");
        if (!(requestValue instanceof Map<?, ?> requestMeta)) {
            return "";
        }
        Object path = requestMeta.get("path");
        return path == null ? "" : path.toString();
    }

    private static String extractResponseStatusCode(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object decodedValue = response.get("decoded");
        if (!(decodedValue instanceof Map<?, ?> decoded)) {
            return "";
        }
        Object startLine = decoded.get("startLine");
        if (!(startLine instanceof String line) || !line.startsWith("HTTP/")) {
            return "";
        }
        String[] parts = line.split(" ", 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    static boolean isReplayable(SessionEvent event) {
        return "PAYLOAD".equals(event.type()) && event.direction() == Direction.CLIENT_TO_TARGET;
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
}
