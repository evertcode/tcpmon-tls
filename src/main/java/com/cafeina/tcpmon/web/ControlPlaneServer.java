package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.UiConfig;
import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.replay.ReplayDestination;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.session.SessionEvent;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class ControlPlaneServer implements AutoCloseable {
    private final UiConfig config;
    private final SessionStore sessionStore;
    private final ReplayService replayService;
    private final ObjectMapper objectMapper = JsonSupport.objectMapper();
    private HttpServer server;

    public ControlPlaneServer(UiConfig config, SessionStore sessionStore, ReplayService replayService) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.replayService = replayService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/sessions", this::handleSessions);
        server.createContext("/api/pending/", this::handlePending);
        server.createContext("/api/replay", this::handleReplay);
        server.setExecutor(Executors.newCachedThreadPool());
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
            sendJson(exchange, 200, Map.of("sessions", sessionStore.listSessions()));
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

    private void handlePending(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().endsWith("/forward")) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String pendingId = path.substring("/api/pending/".length(), path.length() - "/forward".length());
        JsonNode body = readJsonBody(exchange);
        byte[] replacement = null;
        if (body.hasNonNull("content")) {
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
        String base64 = event.details().get("base64").toString();
        try {
            ReplayDestination destination = ReplayDestination.fromString(body.path("destination").asText("listener"));
            Map<String, Object> result = replayService.replay(java.util.Base64.getDecoder().decode(base64), destination);
            sendJson(exchange, 200, result);
        } catch (Exception exception) {
            sendJson(exchange, 500, Map.of("error", exception.toString()));
        }
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

    static boolean isReplayable(SessionEvent event) {
        return "PAYLOAD".equals(event.type()) && event.direction() == Direction.CLIENT_TO_TARGET;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }
}
