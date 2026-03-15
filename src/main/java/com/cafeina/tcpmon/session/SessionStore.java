package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TransportMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class SessionStore implements AutoCloseable {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Path rootDir;
    private final Path dbPath;
    private final ObjectMapper objectMapper;
    private final Connection connection;
    private final Map<String, PendingPayload> pendingPayloads = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SessionChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    public SessionStore(Path rootDir, ObjectMapper objectMapper) throws IOException {
        this.rootDir = rootDir;
        this.dbPath = rootDir.resolve("sessions.db");
        this.objectMapper = objectMapper;
        Files.createDirectories(this.rootDir);
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            this.connection.setAutoCommit(true);
            initializeSchema();
        } catch (SQLException exception) {
            throw new IOException("Unable to open SQLite session store", exception);
        }
    }

    public synchronized String openSession(String routeId, String clientAddress, String listenerAddress, String targetAddress) {
        String sessionId = "s-" + ids.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into sessions (
                    session_id,
                    route_id,
                    started_at,
                    ended_at,
                    status,
                    client_address,
                    listener_address,
                    target_address,
                    inbound_tls_json,
                    outbound_tls_json
                ) values (?, ?, ?, null, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, routeId);
            statement.setString(3, now.toString());
            statement.setString(4, "OPEN");
            statement.setString(5, clientAddress);
            statement.setString(6, listenerAddress);
            statement.setString(7, targetAddress);
            statement.setString(8, "{}");
            statement.setString(9, "{}");
            statement.executeUpdate();
            publishChange(new SessionChangeEvent("session-created", sessionId, routeId, now, null));
            return sessionId;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to open session " + sessionId, exception);
        }
    }

    public synchronized void closeSession(String sessionId, String status) {
        requireSessionExists(sessionId);
        try (PreparedStatement statement = connection.prepareStatement("""
                update sessions
                set status = ?, ended_at = ?
                where session_id = ?
                """)) {
            statement.setString(1, status);
            statement.setString(2, Instant.now().toString());
            statement.setString(3, sessionId);
            statement.executeUpdate();
            publishChange(new SessionChangeEvent("session-closed", sessionId, routeIdForSession(sessionId), Instant.now(), status));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to close session " + sessionId, exception);
        }
    }

    public synchronized void recordLifecycle(String sessionId, String type, Map<String, Object> details) {
        requireSessionExists(sessionId);
        Instant timestamp = Instant.now();
        SessionEvent event = new SessionEvent(nextEventId(), type, timestamp, null, 0, null, null, null, null, details);
        insertEvent(sessionId, event, null);
        if (!"PENDING_RELEASED".equals(type)) {
            publishChange(new SessionChangeEvent("session-updated", sessionId, routeIdForSession(sessionId), timestamp, type));
        }
    }

    public synchronized void recordTls(String sessionId, boolean inbound, Map<String, Object> details) {
        requireSessionExists(sessionId);
        mergeTlsDetails(sessionId, inbound, details);
        recordLifecycle(sessionId, inbound ? "TLS_INBOUND" : "TLS_OUTBOUND", details);
    }

    public synchronized SessionEvent recordPayload(String sessionId, Direction direction, byte[] payload, String pendingId, Map<String, Object> extraDetails) {
        requireSessionExists(sessionId);
        String eventId = nextEventId();
        Map<String, Object> details = new LinkedHashMap<>(extraDetails);
        details.put("base64", Base64.getEncoder().encodeToString(payload));
        SessionEvent event = new SessionEvent(
                eventId,
                "PAYLOAD",
                Instant.now(),
                direction,
                payload.length,
                "sqlite:" + eventId,
                previewUtf8(payload),
                previewHex(payload),
                pendingId,
                details);
        insertEvent(sessionId, event, payload);
        publishChange(new SessionChangeEvent("session-updated", sessionId, routeIdForSession(sessionId), event.timestamp(), "PAYLOAD"));
        return event;
    }

    public PendingPayload addPending(String sessionId, Direction direction, byte[] payload, Consumer<byte[]> forwarder) {
        requireSessionExists(sessionId);
        String pendingId = "p-" + ids.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        PendingPayload pendingPayload = new PendingPayload(pendingId, sessionId, direction, Instant.now(), payload, forwarder);
        pendingPayloads.put(pendingId, pendingPayload);
        return pendingPayload;
    }

    public boolean releasePending(String pendingId, byte[] replacementPayload) {
        PendingPayload pendingPayload = pendingPayloads.remove(pendingId);
        if (pendingPayload == null) {
            return false;
        }
        byte[] finalPayload = replacementPayload == null ? pendingPayload.originalBytes() : replacementPayload;
        pendingPayload.forwarder().accept(finalPayload);
        recordLifecycle(pendingPayload.sessionId(), "PENDING_RELEASED", Map.of(
                "pendingId", pendingId,
                "edited", replacementPayload != null,
                "finalSize", finalPayload.length));
        publishChange(new SessionChangeEvent(
                "pending-released",
                pendingPayload.sessionId(),
                routeIdForSession(pendingPayload.sessionId()),
                Instant.now(),
                pendingId));
        return true;
    }

    public void addChangeListener(SessionChangeListener listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(SessionChangeListener listener) {
        changeListeners.remove(listener);
    }

    public synchronized List<Map<String, Object>> listSessions() {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    s.session_id,
                    s.route_id,
                    s.started_at,
                    s.ended_at,
                    s.status,
                    s.client_address,
                    s.listener_address,
                    s.target_address,
                    (select count(*) from session_events e where e.session_id = s.session_id) as event_count
                from sessions s
                order by s.started_at desc
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> sessions = new ArrayList<>();
            while (resultSet.next()) {
                String sessionId = resultSet.getString("session_id");
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sessionId", sessionId);
                payload.put("routeId", resultSet.getString("route_id"));
                payload.put("startedAt", parseInstant(resultSet.getString("started_at")));
                payload.put("endedAt", parseInstant(resultSet.getString("ended_at")));
                payload.put("status", resultSet.getString("status"));
                payload.put("clientAddress", resultSet.getString("client_address"));
                payload.put("listenerAddress", resultSet.getString("listener_address"));
                payload.put("targetAddress", resultSet.getString("target_address"));
                payload.put("eventCount", resultSet.getInt("event_count"));
                payload.put("pendingCount", pendingCount(sessionId));
                sessions.add(payload);
            }
            return sessions;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to list sessions", exception);
        }
    }

    public synchronized Map<String, Object> sessionDetails(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    session_id,
                    route_id,
                    started_at,
                    ended_at,
                    status,
                    client_address,
                    listener_address,
                    target_address,
                    inbound_tls_json,
                    outbound_tls_json
                from sessions
                where session_id = ?
                """)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sessionId", resultSet.getString("session_id"));
                payload.put("routeId", resultSet.getString("route_id"));
                payload.put("startedAt", parseInstant(resultSet.getString("started_at")));
                payload.put("endedAt", parseInstant(resultSet.getString("ended_at")));
                payload.put("status", resultSet.getString("status"));
                payload.put("clientAddress", resultSet.getString("client_address"));
                payload.put("listenerAddress", resultSet.getString("listener_address"));
                payload.put("targetAddress", resultSet.getString("target_address"));
                payload.put("inboundTls", readMap(resultSet.getString("inbound_tls_json")));
                payload.put("outboundTls", readMap(resultSet.getString("outbound_tls_json")));
                payload.put("events", loadEvents(sessionId));
                payload.put("pendingPayloads", pendingPayloads(sessionId));
                return payload;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load session " + sessionId, exception);
        }
    }

    public synchronized SessionEvent findEvent(String sessionId, String eventId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    event_id,
                    type,
                    timestamp,
                    direction,
                    size,
                    payload_path,
                    preview_text,
                    preview_hex,
                    pending_id,
                    details_json,
                    payload_bytes
                from session_events
                where session_id = ? and event_id = ?
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapEvent(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load event " + eventId + " for session " + sessionId, exception);
        }
    }

    public synchronized String routeIdForSession(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("select route_id from sessions where session_id = ?")) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to resolve route for session " + sessionId, exception);
        }
    }

    public List<PendingPayload> pendingPayloads(String sessionId) {
        return pendingPayloads.values().stream()
                .filter(payload -> payload.sessionId().equals(sessionId))
                .toList();
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma foreign_keys = on");
            statement.execute("""
                    create table if not exists sessions (
                        session_id text primary key,
                        route_id text not null,
                        started_at text not null,
                        ended_at text,
                        status text not null,
                        client_address text,
                        listener_address text,
                        target_address text,
                        inbound_tls_json text not null default '{}',
                        outbound_tls_json text not null default '{}'
                    )
                    """);
            statement.execute("""
                    create table if not exists session_events (
                        session_id text not null,
                        event_id text not null,
                        type text not null,
                        timestamp text not null,
                        direction text,
                        size integer not null,
                        payload_path text,
                        preview_text text,
                        preview_hex text,
                        pending_id text,
                        details_json text not null,
                        payload_bytes blob,
                        primary key (session_id, event_id),
                        foreign key (session_id) references sessions(session_id) on delete cascade
                    )
                    """);
            statement.execute("create index if not exists idx_session_events_session on session_events(session_id, timestamp)");
            statement.execute("""
                    create table if not exists routes (
                        id                         text primary key,
                        listener_host              text not null default '0.0.0.0',
                        listener_port              integer not null,
                        listener_transport         text not null default 'PLAIN',
                        listener_client_auth       text not null default 'NONE',
                        target_host                text not null,
                        target_port                integer not null,
                        target_transport           text not null default 'PLAIN',
                        target_sni_host            text,
                        target_insecure_trust_all  integer not null default 0,
                        target_verify_hostname     integer not null default 1,
                        target_rewrite_host_header integer not null default 0
                    )
                    """);
        }
    }

    private void mergeTlsDetails(String sessionId, boolean inbound, Map<String, Object> details) {
        String column = inbound ? "inbound_tls_json" : "outbound_tls_json";
        try (PreparedStatement select = connection.prepareStatement("select " + column + " from sessions where session_id = ?");
             PreparedStatement update = connection.prepareStatement("update sessions set " + column + " = ? where session_id = ?")) {
            select.setString(1, sessionId);
            Map<String, Object> merged = new LinkedHashMap<>();
            try (ResultSet resultSet = select.executeQuery()) {
                if (resultSet.next()) {
                    merged.putAll(readMap(resultSet.getString(1)));
                }
            }
            merged.putAll(details);
            update.setString(1, writeJson(merged));
            update.setString(2, sessionId);
            update.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update TLS metadata for session " + sessionId, exception);
        }
    }

    private void insertEvent(String sessionId, SessionEvent event, byte[] payloadBytes) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into session_events (
                    session_id,
                    event_id,
                    type,
                    timestamp,
                    direction,
                    size,
                    payload_path,
                    preview_text,
                    preview_hex,
                    pending_id,
                    details_json,
                    payload_bytes
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, event.eventId());
            statement.setString(3, event.type());
            statement.setString(4, event.timestamp().toString());
            if (event.direction() == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, event.direction().name());
            }
            statement.setInt(6, event.size());
            statement.setString(7, event.payloadPath());
            statement.setString(8, event.previewText());
            statement.setString(9, event.previewHex());
            statement.setString(10, event.pendingId());
            statement.setString(11, writeJson(stripBase64(event.details())));
            if (payloadBytes == null) {
                statement.setNull(12, Types.BLOB);
            } else {
                statement.setBytes(12, payloadBytes);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist event " + event.eventId() + " for session " + sessionId, exception);
        }
    }

    private List<SessionEvent> loadEvents(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    event_id,
                    type,
                    timestamp,
                    direction,
                    size,
                    payload_path,
                    preview_text,
                    preview_hex,
                    pending_id,
                    details_json,
                    payload_bytes
                from session_events
                where session_id = ?
                order by timestamp asc, event_id asc
                """)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SessionEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
                return events;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load events for session " + sessionId, exception);
        }
    }

    private SessionEvent mapEvent(ResultSet resultSet) throws SQLException {
        Map<String, Object> details = readMap(resultSet.getString("details_json"));
        byte[] payloadBytes = resultSet.getBytes("payload_bytes");
        if (payloadBytes != null) {
            details.put("base64", Base64.getEncoder().encodeToString(payloadBytes));
        }
        String direction = resultSet.getString("direction");
        return new SessionEvent(
                resultSet.getString("event_id"),
                resultSet.getString("type"),
                parseInstant(resultSet.getString("timestamp")),
                direction == null ? null : Direction.valueOf(direction),
                resultSet.getInt("size"),
                resultSet.getString("payload_path"),
                resultSet.getString("preview_text"),
                resultSet.getString("preview_hex"),
                resultSet.getString("pending_id"),
                details);
    }

    private void requireSessionExists(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from sessions where session_id = ?")) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Unknown session: " + sessionId);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to verify session " + sessionId, exception);
        }
    }

    private void publishChange(SessionChangeEvent event) {
        for (SessionChangeListener listener : changeListeners) {
            listener.onSessionChange(event);
        }
    }

    private int pendingCount(String sessionId) {
        int count = 0;
        for (PendingPayload pendingPayload : pendingPayloads.values()) {
            if (pendingPayload.sessionId().equals(sessionId)) {
                count++;
            }
        }
        return count;
    }

    private String nextEventId() {
        return "e-" + ids.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read JSON payload", exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write JSON payload", exception);
        }
    }

    private static Map<String, Object> stripBase64(Map<String, Object> details) {
        Map<String, Object> cleaned = new LinkedHashMap<>(details);
        cleaned.remove("base64");
        return cleaned;
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String previewUtf8(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ".");
        return text.substring(0, Math.min(120, text.length()));
    }

    private static String previewHex(byte[] payload) {
        int limit = Math.min(payload.length, 48);
        StringBuilder builder = new StringBuilder(limit * 3);
        for (int index = 0; index < limit; index++) {
            builder.append(String.format("%02x", payload[index]));
            if (index < limit - 1) {
                builder.append(' ');
            }
        }
        if (payload.length > limit) {
            builder.append(" ...");
        }
        return builder.toString();
    }

    public synchronized int countRoutes() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from routes")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count routes", exception);
        }
    }

    public synchronized List<RouteConfig> loadRoutes() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select * from routes")) {
            List<RouteConfig> routes = new ArrayList<>();
            while (resultSet.next()) {
                routes.add(mapRoute(resultSet));
            }
            return routes;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load routes", exception);
        }
    }

    public synchronized void insertRoute(RouteConfig route) {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into routes (
                    id, listener_host, listener_port, listener_transport, listener_client_auth,
                    target_host, target_port, target_transport, target_sni_host,
                    target_insecure_trust_all, target_verify_hostname, target_rewrite_host_header
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            setRouteParams(statement, route);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert route " + route.id(), exception);
        }
    }

    public synchronized void updateRoute(RouteConfig route) {
        try (PreparedStatement statement = connection.prepareStatement("""
                update routes set
                    listener_host = ?, listener_port = ?, listener_transport = ?, listener_client_auth = ?,
                    target_host = ?, target_port = ?, target_transport = ?, target_sni_host = ?,
                    target_insecure_trust_all = ?, target_verify_hostname = ?, target_rewrite_host_header = ?
                where id = ?
                """)) {
            statement.setString(1, route.listener().host());
            statement.setInt(2, route.listener().port());
            statement.setString(3, route.listener().transportMode().name());
            statement.setString(4, route.listener().clientAuthMode().name());
            statement.setString(5, route.target().host());
            statement.setInt(6, route.target().port());
            statement.setString(7, route.target().transportMode().name());
            if (route.target().sniHost() != null) {
                statement.setString(8, route.target().sniHost());
            } else {
                statement.setNull(8, Types.VARCHAR);
            }
            statement.setInt(9, route.target().insecureTrustAll() ? 1 : 0);
            statement.setInt(10, route.target().verifyHostname() ? 1 : 0);
            statement.setInt(11, route.target().rewriteHostHeader() ? 1 : 0);
            statement.setString(12, route.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update route " + route.id(), exception);
        }
    }

    public synchronized void deleteRoute(String id) {
        try (PreparedStatement statement = connection.prepareStatement("delete from routes where id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete route " + id, exception);
        }
    }

    private void setRouteParams(PreparedStatement statement, RouteConfig route) throws SQLException {
        statement.setString(1, route.id());
        statement.setString(2, route.listener().host());
        statement.setInt(3, route.listener().port());
        statement.setString(4, route.listener().transportMode().name());
        statement.setString(5, route.listener().clientAuthMode().name());
        statement.setString(6, route.target().host());
        statement.setInt(7, route.target().port());
        statement.setString(8, route.target().transportMode().name());
        if (route.target().sniHost() != null) {
            statement.setString(9, route.target().sniHost());
        } else {
            statement.setNull(9, Types.VARCHAR);
        }
        statement.setInt(10, route.target().insecureTrustAll() ? 1 : 0);
        statement.setInt(11, route.target().verifyHostname() ? 1 : 0);
        statement.setInt(12, route.target().rewriteHostHeader() ? 1 : 0);
    }

    private RouteConfig mapRoute(ResultSet resultSet) throws SQLException {
        ListenerConfig listener = new ListenerConfig(
                resultSet.getString("listener_host"),
                resultSet.getInt("listener_port"),
                TransportMode.valueOf(resultSet.getString("listener_transport")),
                ClientAuthMode.valueOf(resultSet.getString("listener_client_auth")),
                null);
        TargetConfig target = new TargetConfig(
                resultSet.getString("target_host"),
                resultSet.getInt("target_port"),
                TransportMode.valueOf(resultSet.getString("target_transport")),
                resultSet.getString("target_sni_host"),
                resultSet.getInt("target_insecure_trust_all") == 1,
                resultSet.getInt("target_verify_hostname") == 1,
                resultSet.getInt("target_rewrite_host_header") == 1,
                null);
        return new RouteConfig(resultSet.getString("id"), listener, target);
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new UncheckedIOException(new IOException("Unable to close SQLite session store", exception));
        }
    }
}
