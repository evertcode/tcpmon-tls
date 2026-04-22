package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.security.PasswordEncryptor;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SessionStore implements AutoCloseable {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int SCHEMA_VERSION = 3;
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");

    private final Path rootDir;
    private final Path dbPath;
    private final ObjectMapper objectMapper;
    private final PasswordEncryptor passwordEncryptor;
    private final Connection connection;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(new SessionWriterThreadFactory());
    private final Map<String, PendingPayload> pendingPayloads = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRouteIds = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SessionChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicBoolean closing = new AtomicBoolean();
    private boolean backfillComplete = false;

    public record PayloadChunk(
            Instant timestamp,
            Direction direction,
            int size,
            byte[] bytes) {
    }

    public SessionStore(Path rootDir, ObjectMapper objectMapper) throws IOException {
        this(rootDir, objectMapper, loadEncryptor(rootDir));
    }

    public SessionStore(Path rootDir, ObjectMapper objectMapper, PasswordEncryptor passwordEncryptor) throws IOException {
        this.rootDir = rootDir;
        this.dbPath = rootDir.resolve("sessions.db");
        this.objectMapper = objectMapper;
        this.passwordEncryptor = passwordEncryptor;
        Files.createDirectories(this.rootDir);
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            this.connection.setAutoCommit(true);
            initializeSchema();
        } catch (SQLException exception) {
            throw new IOException("Unable to open SQLite session store", exception);
        }
    }

    private static PasswordEncryptor loadEncryptor(Path rootDir) throws IOException {
        Files.createDirectories(rootDir);
        return PasswordEncryptor.fromKeyFile(rootDir.resolve("db.key"));
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
            sessionRouteIds.put(sessionId, routeId);
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
            closeOpenExchangeSummaries(sessionId);
            publishChange(new SessionChangeEvent("session-closed", sessionId, routeIdForSession(sessionId), Instant.now(), status));
            sessionRouteIds.remove(sessionId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to close session " + sessionId, exception);
        }
    }

    public void closeSessionAsync(String sessionId, String status) {
        submitWrite(() -> closeSession(sessionId, status));
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

    public void recordLifecycleAsync(String sessionId, String type, Map<String, Object> details) {
        submitWrite(() -> recordLifecycle(sessionId, type, details));
    }

    public synchronized void recordTls(String sessionId, boolean inbound, Map<String, Object> details) {
        requireSessionExists(sessionId);
        mergeTlsDetails(sessionId, inbound, details);
        recordLifecycle(sessionId, inbound ? "TLS_INBOUND" : "TLS_OUTBOUND", details);
    }

    public void recordTlsAsync(String sessionId, boolean inbound, Map<String, Object> details) {
        submitWrite(() -> recordTls(sessionId, inbound, details));
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
        rebuildExchangeSummaries(sessionId);
        publishChange(new SessionChangeEvent("session-updated", sessionId, routeIdForSession(sessionId), event.timestamp(), "PAYLOAD"));
        return event;
    }

    public void recordPayloadAsync(String sessionId, Direction direction, byte[] payload, String pendingId, Map<String, Object> extraDetails) {
        submitWrite(() -> recordPayload(sessionId, direction, payload, pendingId, extraDetails));
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
        submitWrite(() -> {
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
        });
        return true;
    }

    public void addChangeListener(SessionChangeListener listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(SessionChangeListener listener) {
        changeListeners.remove(listener);
    }

    public synchronized List<Map<String, Object>> listSessions() {
        backfillMissingExchangeSummaries();
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
                    (select count(*) from session_events e where e.session_id = s.session_id) as event_count,
                    (select x.request_method from session_exchanges x
                     where x.session_id = s.session_id order by x.exchange_index desc limit 1) as latest_request_method,
                    (select x.request_path from session_exchanges x
                     where x.session_id = s.session_id order by x.exchange_index desc limit 1) as latest_request_path,
                    (select x.response_status_code from session_exchanges x
                     where x.session_id = s.session_id order by x.exchange_index desc limit 1) as latest_response_status_code,
                    (select x.ended_at from session_exchanges x
                     where x.session_id = s.session_id order by x.exchange_index desc limit 1) as latest_ended_at,
                    (select x.response_size_bytes from session_exchanges x
                     where x.session_id = s.session_id order by x.exchange_index desc limit 1) as latest_response_size_bytes,
                    (select count(*) from session_exchanges x
                     where x.session_id = s.session_id and x.status = 'OPEN') as open_exchange_count
                from sessions s
                order by s.started_at desc
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> sessions = new ArrayList<>();
            while (resultSet.next()) {
                String sessionId = resultSet.getString("session_id");
                String sessionStatus = resultSet.getString("status");
                int openExchangeCount = resultSet.getInt("open_exchange_count");
                boolean live = "OPEN".equalsIgnoreCase(sessionStatus) && openExchangeCount > 0;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sessionId", sessionId);
                payload.put("routeId", resultSet.getString("route_id"));
                payload.put("startedAt", parseInstant(resultSet.getString("started_at")));
                payload.put("endedAt", parseInstant(resultSet.getString("ended_at")));
                payload.put("status", sessionStatus);
                payload.put("clientAddress", resultSet.getString("client_address"));
                payload.put("listenerAddress", resultSet.getString("listener_address"));
                payload.put("targetAddress", resultSet.getString("target_address"));
                payload.put("eventCount", resultSet.getInt("event_count"));
                payload.put("pendingCount", pendingCount(sessionId));
                payload.put("requestMethod", emptyIfNull(resultSet.getString("latest_request_method")));
                payload.put("requestPath", emptyIfNull(resultSet.getString("latest_request_path")));
                payload.put("responseStatusCode", emptyIfNull(resultSet.getString("latest_response_status_code")));
                payload.put("live", live);
                Instant sessionStart = parseInstant(resultSet.getString("started_at"));
                Instant latestEnd = parseInstant(resultSet.getString("latest_ended_at"));
                if (sessionStart != null && latestEnd != null && !latestEnd.isBefore(sessionStart)) {
                    payload.put("durationMs", java.time.Duration.between(sessionStart, latestEnd).toMillis());
                }
                long responseSize = resultSet.getLong("latest_response_size_bytes");
                if (!resultSet.wasNull()) {
                    payload.put("responseSizeBytes", responseSize);
                }
                sessions.add(payload);
            }
            return sessions;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to list sessions", exception);
        }
    }

    public synchronized Map<String, Map<String, Object>> listRouteStats() {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    e.route_id,
                    count(*) as request_count,
                    sum(case when s.status = 'OPEN' and e.status = 'OPEN' then 1 else 0 end) as live_count,
                    sum(case when e.response_status_code like '5%' then 1 else 0 end) as error_count,
                    cast(avg(e.duration_ms) as integer) as avg_duration_ms
                from session_exchanges e
                join sessions s on s.session_id = e.session_id
                group by e.route_id
                """);
             ResultSet rs = statement.executeQuery()) {
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            while (rs.next()) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("requestCount", rs.getLong("request_count"));
                stats.put("liveCount", rs.getLong("live_count"));
                stats.put("errorCount", rs.getLong("error_count"));
                long avgDuration = rs.getLong("avg_duration_ms");
                if (!rs.wasNull()) {
                    stats.put("avgDurationMs", avgDuration);
                }
                result.put(rs.getString("route_id"), stats);
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to compute route stats", exception);
        }
    }

    public synchronized Map<String, Object> listRequestRowsPaginated(
            String routeId, int limit, String cursor, String method, String statusCode, String q) {
        backfillMissingExchangeSummaries();
        StringBuilder sql = new StringBuilder("""
                select
                    e.session_id,
                    e.route_id,
                    e.exchange_index,
                    e.started_at,
                    e.ended_at,
                    e.request_method,
                    e.request_path,
                    e.response_status_code,
                    e.request_size_bytes,
                    e.response_size_bytes,
                    e.duration_ms,
                    e.status as exchange_status,
                    s.status as session_status,
                    s.client_address,
                    s.listener_address,
                    s.target_address
                from session_exchanges e
                join sessions s on s.session_id = e.session_id
                where e.route_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(routeId);
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|", 3);
            if (parts.length == 3) {
                String cursorAt = parts[0];
                String cursorSid = parts[1];
                int cursorIdx;
                try {
                    cursorIdx = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    cursorIdx = 0;
                }
                sql.append("""
                        and (e.started_at < ?
                             or (e.started_at = ? and e.session_id < ?)
                             or (e.started_at = ? and e.session_id = ? and e.exchange_index > ?))
                        """);
                params.add(cursorAt); params.add(cursorAt); params.add(cursorSid);
                params.add(cursorAt); params.add(cursorSid); params.add(cursorIdx);
            }
        }
        if (method != null && !method.isBlank()) {
            sql.append("and e.request_method = ?\n");
            params.add(method);
        }
        if (statusCode != null && !statusCode.isBlank()) {
            sql.append("and e.response_status_code = ?\n");
            params.add(statusCode);
        }
        if (q != null && !q.isBlank()) {
            sql.append("and (e.request_path like ? escape '\\' or e.session_id like ? escape '\\')\n");
            String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like);
            params.add(like);
        }
        sql.append("order by e.started_at desc, e.session_id desc, e.exchange_index asc\nlimit ?\n");
        params.add(limit + 1);
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                setParam(statement, i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRequestRow(rs));
                }
                boolean hasMore = rows.size() > limit;
                if (hasMore) {
                    rows.removeLast();
                }
                String nextCursor = null;
                if (hasMore && !rows.isEmpty()) {
                    Map<String, Object> last = rows.getLast();
                    Instant startedAt = last.get("startedAt") instanceof Instant inst ? inst : null;
                    String sid = String.valueOf(last.get("sessionId"));
                    int idx = ((Number) last.get("exchangeIndex")).intValue();
                    nextCursor = (startedAt != null ? startedAt.toString() : "") + "|" + sid + "|" + idx;
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("requests", rows);
                result.put("nextCursor", nextCursor);
                result.put("hasMore", hasMore);
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to list paginated request rows for route " + routeId, exception);
        }
    }

    public synchronized Map<String, Object> requestFacets(String routeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement("""
                select distinct request_method
                from session_exchanges
                where route_id = ? and request_method is not null and request_method != ''
                order by request_method
                """)) {
            stmt.setString(1, routeId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> methods = new ArrayList<>();
                while (rs.next()) {
                    methods.add(rs.getString(1));
                }
                result.put("methods", methods);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get methods for route " + routeId, e);
        }
        try (PreparedStatement stmt = connection.prepareStatement("""
                select distinct response_status_code
                from session_exchanges
                where route_id = ? and response_status_code is not null and response_status_code != ''
                order by response_status_code
                """)) {
            stmt.setString(1, routeId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> codes = new ArrayList<>();
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
                result.put("statusCodes", codes);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get status codes for route " + routeId, e);
        }
        try (PreparedStatement stmt = connection.prepareStatement("""
                select
                    count(*) as total_requests,
                    sum(case when s.status = 'OPEN' and e.status = 'OPEN' then 1 else 0 end) as live_requests,
                    sum(case when e.response_status_code like '5%' then 1 else 0 end) as error_count,
                    cast(avg(e.duration_ms) as integer) as avg_duration_ms
                from session_exchanges e
                join sessions s on s.session_id = e.session_id
                where e.route_id = ?
                """)) {
            stmt.setString(1, routeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result.put("totalRequests", rs.getLong("total_requests"));
                    result.put("liveRequests", rs.getLong("live_requests"));
                    result.put("errorCount", rs.getLong("error_count"));
                    long avg = rs.getLong("avg_duration_ms");
                    if (!rs.wasNull()) {
                        result.put("avgDurationMs", avg);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get facets for route " + routeId, e);
        }
        return result;
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

    public synchronized List<PayloadChunk> payloadChunks(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    timestamp,
                    direction,
                    size,
                    payload_bytes
                from session_events
                where session_id = ?
                  and type = 'PAYLOAD'
                order by timestamp asc, event_id asc
                """)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PayloadChunk> chunks = new ArrayList<>();
                while (resultSet.next()) {
                    String direction = resultSet.getString("direction");
                    byte[] bytes = resultSet.getBytes("payload_bytes");
                    if (direction != null && bytes != null) {
                        chunks.add(new PayloadChunk(
                                parseInstant(resultSet.getString("timestamp")),
                                Direction.valueOf(direction),
                                resultSet.getInt("size"),
                                bytes));
                    }
                }
                return chunks;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load payload chunks for session " + sessionId, exception);
        }
    }

    public synchronized List<Map<String, Object>> listRequestRows() {
        backfillMissingExchangeSummaries();
        try (PreparedStatement statement = connection.prepareStatement("""
                select
                    e.session_id,
                    e.route_id,
                    e.exchange_index,
                    e.started_at,
                    e.ended_at,
                    e.request_method,
                    e.request_path,
                    e.response_status_code,
                    e.request_size_bytes,
                    e.response_size_bytes,
                    e.duration_ms,
                    e.status as exchange_status,
                    s.status as session_status,
                    s.client_address,
                    s.listener_address,
                    s.target_address,
                    (select count(*) from session_events ev where ev.session_id = e.session_id) as event_count
                from session_exchanges e
                join sessions s on s.session_id = e.session_id
                order by e.started_at desc, e.session_id desc, e.exchange_index asc
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                String sessionId = resultSet.getString("session_id");
                String sessionStatus = resultSet.getString("session_status");
                String exchangeStatus = resultSet.getString("exchange_status");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionId", sessionId);
                row.put("routeId", resultSet.getString("route_id"));
                row.put("startedAt", parseInstant(resultSet.getString("started_at")));
                row.put("endedAt", parseInstant(resultSet.getString("ended_at")));
                row.put("status", sessionStatus);
                row.put("clientAddress", resultSet.getString("client_address"));
                row.put("listenerAddress", resultSet.getString("listener_address"));
                row.put("targetAddress", resultSet.getString("target_address"));
                row.put("eventCount", resultSet.getInt("event_count"));
                row.put("pendingCount", pendingCount(sessionId));
                row.put("exchangeIndex", resultSet.getInt("exchange_index"));
                row.put("requestMethod", emptyIfNull(resultSet.getString("request_method")));
                row.put("requestPath", emptyIfNull(resultSet.getString("request_path")));
                row.put("responseStatusCode", emptyIfNull(resultSet.getString("response_status_code")));
                long durationMs = resultSet.getLong("duration_ms");
                if (!resultSet.wasNull()) {
                    row.put("durationMs", durationMs);
                }
                long responseSize = resultSet.getLong("response_size_bytes");
                if (!resultSet.wasNull()) {
                    row.put("responseSizeBytes", responseSize);
                }
                row.put("live", "OPEN".equalsIgnoreCase(sessionStatus) && "OPEN".equalsIgnoreCase(exchangeStatus));
                rows.add(row);
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to list request rows", exception);
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
        String cached = sessionRouteIds.get(sessionId);
        if (cached != null) {
            return cached;
        }
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
        }
        int currentVersion = readSchemaVersion();
        if (currentVersion == 0) {
            currentVersion = detectLegacySchemaVersion();
        }
        applyMigrations(currentVersion);
    }

    private void applyMigrations(int currentVersion) throws SQLException {
        if (currentVersion < 1) {
            migrateToVersion1();
            currentVersion = 1;
        }
        if (currentVersion < 2) {
            migrateToVersion2();
            currentVersion = 2;
        }
        if (currentVersion < 3) {
            migrateToVersion3();
            currentVersion = 3;
        }
        if (currentVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported schema version: " + currentVersion);
        }
    }

    private void migrateToVersion1() throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
        writeSchemaVersion(1);
    }

    private void migrateToVersion2() throws SQLException {
        String[][] cols = {
            {"listener_tls_cert", "TEXT"}, {"listener_tls_key", "TEXT"},
            {"listener_tls_keystore", "TEXT"}, {"listener_tls_keystore_password", "TEXT"},
            {"listener_tls_keystore_type", "TEXT"}, {"listener_tls_truststore", "TEXT"},
            {"listener_tls_truststore_password", "TEXT"}, {"listener_tls_truststore_type", "TEXT"},
            {"target_tls_cert", "TEXT"}, {"target_tls_key", "TEXT"},
            {"target_tls_keystore", "TEXT"}, {"target_tls_keystore_password", "TEXT"},
            {"target_tls_keystore_type", "TEXT"}, {"target_tls_truststore", "TEXT"},
            {"target_tls_truststore_password", "TEXT"}, {"target_tls_truststore_type", "TEXT"},
        };
        for (String[] col : cols) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE routes ADD COLUMN " + col[0] + " " + col[1]);
            } catch (SQLException ignored) {
                // column already exists
            }
        }
        writeSchemaVersion(2);
    }

    private void migrateToVersion3() throws SQLException {
        createSessionExchangesTable();
        writeSchemaVersion(3);
    }

    private void createSessionExchangesTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists session_exchanges (
                        session_id text not null,
                        route_id text not null,
                        exchange_index integer not null,
                        started_at text,
                        ended_at text,
                        request_method text,
                        request_path text,
                        response_status_code text,
                        request_size_bytes integer,
                        response_size_bytes integer,
                        duration_ms integer,
                        status text not null default 'OPEN',
                        primary key (session_id, exchange_index),
                        foreign key (session_id) references sessions(session_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create index if not exists idx_session_exchanges_route_started
                    on session_exchanges(route_id, started_at desc, session_id desc, exchange_index asc)
                    """);
            statement.execute("""
                    create index if not exists idx_session_exchanges_route_method
                    on session_exchanges(route_id, request_method)
                    """);
            statement.execute("""
                    create index if not exists idx_session_exchanges_route_status
                    on session_exchanges(route_id, response_status_code)
                    """);
        }
    }

    private int detectLegacySchemaVersion() throws SQLException {
        if (!tableExists("sessions") && !tableExists("routes") && !tableExists("session_events")) {
            return 0;
        }
        if (tableExists("session_exchanges")) {
            writeSchemaVersion(3);
            return 3;
        }
        if (tableExists("routes") && columnExists("routes", "listener_tls_cert")) {
            writeSchemaVersion(2);
            return 2;
        }
        writeSchemaVersion(1);
        return 1;
    }

    private int readSchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("pragma user_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void writeSchemaVersion(int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma user_version = " + version);
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from sqlite_master
                where type = 'table' and name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void rebuildExchangeSummaries(String sessionId) {
        String routeId = routeIdForSession(sessionId);
        List<PayloadChunk> chunks = payloadChunks(sessionId);
        List<PayloadMessageSummary> requests = summarizePayloadMessages(chunks, Direction.CLIENT_TO_TARGET);
        List<PayloadMessageSummary> responses = summarizePayloadMessages(chunks, Direction.TARGET_TO_CLIENT);
        try (PreparedStatement delete = connection.prepareStatement("delete from session_exchanges where session_id = ?");
             PreparedStatement insert = connection.prepareStatement("""
                     insert into session_exchanges (
                         session_id,
                         route_id,
                         exchange_index,
                         started_at,
                         ended_at,
                         request_method,
                         request_path,
                         response_status_code,
                         request_size_bytes,
                         response_size_bytes,
                         duration_ms,
                         status
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            delete.setString(1, sessionId);
            delete.executeUpdate();

            for (int index = 0; index < requests.size(); index++) {
                PayloadMessageSummary request = requests.get(index);
                PayloadMessageSummary response = index < responses.size() ? responses.get(index) : null;
                Long durationMs = requestDuration(request, response);
                insert.setString(1, sessionId);
                insert.setString(2, routeId);
                insert.setInt(3, index);
                setInstant(insert, 4, request.timestamp());
                setInstant(insert, 5, response == null ? null : response.timestamp());
                insert.setString(6, request.requestMethod());
                insert.setString(7, request.requestPath());
                insert.setString(8, response == null ? "" : response.responseStatusCode());
                insert.setInt(9, request.size());
                if (response == null) {
                    insert.setNull(10, Types.INTEGER);
                    insert.setNull(11, Types.INTEGER);
                    insert.setString(12, "OPEN");
                } else {
                    insert.setInt(10, response.size());
                    if (durationMs == null) {
                        insert.setNull(11, Types.INTEGER);
                    } else {
                        insert.setLong(11, durationMs);
                    }
                    insert.setString(12, "COMPLETE");
                }
                insert.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to rebuild exchange summaries for session " + sessionId, exception);
        }
    }

    private void backfillMissingExchangeSummaries() {
        if (backfillComplete) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                select s.session_id
                from sessions s
                where exists (
                    select 1
                    from session_events e
                    where e.session_id = s.session_id
                      and e.type = 'PAYLOAD'
                )
                and not exists (
                    select 1
                    from session_exchanges x
                    where x.session_id = s.session_id
                )
                order by s.started_at asc
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<String> sessionIds = new ArrayList<>();
            while (resultSet.next()) {
                sessionIds.add(resultSet.getString("session_id"));
            }
            for (String sessionId : sessionIds) {
                rebuildExchangeSummaries(sessionId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to backfill exchange summaries", exception);
        }
        backfillComplete = true;
    }

    private void closeOpenExchangeSummaries(String sessionId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                update session_exchanges
                set status = 'CLOSED'
                where session_id = ?
                  and status = 'OPEN'
                """)) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to close exchange summaries for session " + sessionId, exception);
        }
    }

    private static List<PayloadMessageSummary> summarizePayloadMessages(List<PayloadChunk> chunks, Direction direction) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        List<Integer> chunkOffsets = new ArrayList<>();
        List<Instant> chunkTimestamps = new ArrayList<>();
        Instant latestTimestamp = null;

        for (PayloadChunk chunk : chunks) {
            if (chunk.direction() != direction) {
                continue;
            }
            chunkOffsets.add(buffer.size());
            chunkTimestamps.add(chunk.timestamp());
            buffer.writeBytes(chunk.bytes());
            latestTimestamp = chunk.timestamp();
        }

        if (latestTimestamp == null) {
            return List.of();
        }

        byte[] payload = buffer.toByteArray();
        List<byte[]> messages = splitHttpMessages(payload);
        if (messages.size() <= 1) {
            return List.of(parsePayloadSummary(payload, latestTimestamp));
        }

        int messageOffset = 0;
        List<PayloadMessageSummary> summaries = new ArrayList<>();
        for (byte[] message : messages) {
            Instant timestamp = timestampAtOffset(messageOffset, chunkOffsets, chunkTimestamps, latestTimestamp);
            summaries.add(parsePayloadSummary(message, timestamp));
            messageOffset += message.length;
        }
        return summaries;
    }

    private static List<byte[]> splitHttpMessages(byte[] payload) {
        List<byte[]> messages = new ArrayList<>();
        int offset = 0;
        while (offset < payload.length) {
            int next = nextMessageEnd(payload, offset);
            if (next <= offset) {
                break;
            }
            byte[] message = java.util.Arrays.copyOfRange(payload, offset, next);
            if (!looksLikeHttpStartLine(message)) {
                break;
            }
            messages.add(message);
            offset = next;
        }
        if (messages.isEmpty()) {
            messages.add(payload);
        }
        return messages;
    }

    private static int nextMessageEnd(byte[] payload, int start) {
        String latin1 = new String(payload, start, payload.length - start, StandardCharsets.ISO_8859_1);
        int headerEndRelative = latin1.indexOf("\r\n\r\n");
        if (headerEndRelative < 0) {
            return payload.length;
        }
        int headerEnd = start + headerEndRelative;
        String headers = latin1.substring(0, headerEndRelative);
        int contentLength = headerValue(headers, "content-length");
        boolean chunked = headerContains(headers, "transfer-encoding", "chunked");
        int bodyStart = headerEnd + 4;
        int messageEnd = chunked
                ? chunkedMessageEnd(payload, bodyStart)
                : bodyStart + Math.max(contentLength, 0);
        return Math.min(messageEnd, payload.length);
    }

    private static int headerValue(String headers, String name) {
        String[] lines = headers.split("\r\n");
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            if (line.substring(0, separator).trim().equalsIgnoreCase(name)) {
                try {
                    return Integer.parseInt(line.substring(separator + 1).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static boolean headerContains(String headers, String name, String expectedValue) {
        String[] lines = headers.split("\r\n");
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String headerName = line.substring(0, separator).trim();
            String headerValue = line.substring(separator + 1).trim().toLowerCase(java.util.Locale.ROOT);
            if (headerName.equalsIgnoreCase(name) && headerValue.contains(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private static int chunkedMessageEnd(byte[] payload, int bodyStart) {
        int offset = bodyStart;
        while (offset < payload.length) {
            int lineEnd = indexOf(payload, offset, "\r\n".getBytes(StandardCharsets.ISO_8859_1));
            if (lineEnd < 0) {
                return payload.length;
            }
            String sizeLine = new String(payload, offset, lineEnd - offset, StandardCharsets.ISO_8859_1).trim();
            int separator = sizeLine.indexOf(';');
            String sizeToken = separator >= 0 ? sizeLine.substring(0, separator) : sizeLine;
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeToken.trim(), 16);
            } catch (NumberFormatException ignored) {
                return payload.length;
            }
            offset = lineEnd + 2;
            if (chunkSize == 0) {
                if (offset + 1 < payload.length && payload[offset] == '\r' && payload[offset + 1] == '\n') {
                    return offset + 2;
                }
                int trailersEnd = indexOf(payload, offset, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                return trailersEnd >= 0 ? trailersEnd + 4 : payload.length;
            }
            offset += chunkSize + 2;
        }
        return payload.length;
    }

    private static boolean looksLikeHttpStartLine(byte[] message) {
        int lineEnd = indexOf(message, 0, "\r\n".getBytes(StandardCharsets.ISO_8859_1));
        if (lineEnd < 0) {
            lineEnd = indexOf(message, 0, "\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        if (lineEnd <= 0) {
            return false;
        }
        String startLine = new String(message, 0, lineEnd, StandardCharsets.ISO_8859_1);
        if (startLine.startsWith("HTTP/")) {
            return true;
        }
        int separator = startLine.indexOf(' ');
        if (separator <= 0) {
            return false;
        }
        return HTTP_METHODS.contains(startLine.substring(0, separator));
    }

    private static PayloadMessageSummary parsePayloadSummary(byte[] payload, Instant timestamp) {
        int lineEnd = indexOf(payload, 0, "\r\n".getBytes(StandardCharsets.ISO_8859_1));
        if (lineEnd < 0) {
            lineEnd = indexOf(payload, 0, "\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int startLineLength = lineEnd < 0 ? payload.length : lineEnd;
        String startLine = new String(payload, 0, startLineLength, StandardCharsets.ISO_8859_1);
        String requestMethod = "";
        String requestPath = "";
        String responseStatusCode = "";

        if (startLine.startsWith("HTTP/")) {
            String[] parts = startLine.split(" ", 3);
            responseStatusCode = parts.length >= 2 ? parts[1] : "";
        } else {
            String[] parts = startLine.split(" ", 3);
            if (parts.length == 3) {
                requestMethod = parts[0];
                String target = parts[1];
                int querySeparator = target.indexOf('?');
                requestPath = querySeparator >= 0 ? target.substring(0, querySeparator) : target;
            }
        }

        return new PayloadMessageSummary(timestamp, payload.length, requestMethod, requestPath, responseStatusCode);
    }

    private static Instant timestampAtOffset(int offset, List<Integer> chunkOffsets, List<Instant> chunkTimestamps, Instant fallback) {
        Instant result = fallback;
        for (int index = 0; index < chunkOffsets.size(); index++) {
            if (chunkOffsets.get(index) <= offset) {
                result = chunkTimestamps.get(index);
            } else {
                break;
            }
        }
        return result;
    }

    private static Long requestDuration(PayloadMessageSummary request, PayloadMessageSummary response) {
        if (request == null || response == null || request.timestamp() == null || response.timestamp() == null
                || response.timestamp().isBefore(request.timestamp())) {
            return null;
        }
        return java.time.Duration.between(request.timestamp(), response.timestamp()).toMillis();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private static int indexOf(byte[] haystack, int start, byte[] needle) {
        outer:
        for (int index = start; index <= haystack.length - needle.length; index++) {
            for (int probe = 0; probe < needle.length; probe++) {
                if (haystack[index + probe] != needle[probe]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private record PayloadMessageSummary(
            Instant timestamp,
            int size,
            String requestMethod,
            String requestPath,
            String responseStatusCode) {
    }

    private Map<String, Object> mapRequestRow(ResultSet rs) throws SQLException {
        String sessionId = rs.getString("session_id");
        String sessionStatus = rs.getString("session_status");
        String exchangeStatus = rs.getString("exchange_status");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("routeId", rs.getString("route_id"));
        row.put("startedAt", parseInstant(rs.getString("started_at")));
        row.put("endedAt", parseInstant(rs.getString("ended_at")));
        row.put("status", sessionStatus);
        row.put("clientAddress", rs.getString("client_address"));
        row.put("listenerAddress", rs.getString("listener_address"));
        row.put("targetAddress", rs.getString("target_address"));
        row.put("exchangeIndex", rs.getInt("exchange_index"));
        row.put("requestMethod", emptyIfNull(rs.getString("request_method")));
        row.put("requestPath", emptyIfNull(rs.getString("request_path")));
        row.put("responseStatusCode", emptyIfNull(rs.getString("response_status_code")));
        long durationMs = rs.getLong("duration_ms");
        if (!rs.wasNull()) {
            row.put("durationMs", durationMs);
        }
        long responseSize = rs.getLong("response_size_bytes");
        if (!rs.wasNull()) {
            row.put("responseSizeBytes", responseSize);
        }
        row.put("live", "OPEN".equalsIgnoreCase(sessionStatus) && "OPEN".equalsIgnoreCase(exchangeStatus));
        return row;
    }

    private static void setParam(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value instanceof String s) {
            stmt.setString(index, s);
        } else if (value instanceof Integer iv) {
            stmt.setInt(index, iv);
        } else if (value instanceof Long lv) {
            stmt.setLong(index, lv);
        } else {
            stmt.setObject(index, value);
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

    public synchronized List<SessionEvent> listPayloadEventsForDirection(String sessionId, Direction direction) {
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
                where session_id = ? and type = 'PAYLOAD' and direction = ?
                order by timestamp asc, event_id asc
                """)) {
            statement.setString(1, sessionId);
            statement.setString(2, direction.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SessionEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
                return events;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load payload events for session " + sessionId, exception);
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

    private void submitWrite(Runnable action) {
        if (closing.get()) {
            return;
        }
        try {
            writeExecutor.execute(() -> {
                if (closing.get()) {
                    return;
                }
                try {
                    action.run();
                } catch (IllegalStateException exception) {
                    if (!closing.get()) {
                        throw exception;
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            if (!closing.get()) {
                throw new IllegalStateException("Session store is shutting down", exception);
            }
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
                    target_insecure_trust_all, target_verify_hostname, target_rewrite_host_header,
                    listener_tls_cert, listener_tls_key, listener_tls_keystore,
                    listener_tls_keystore_password, listener_tls_keystore_type,
                    listener_tls_truststore, listener_tls_truststore_password, listener_tls_truststore_type,
                    target_tls_cert, target_tls_key, target_tls_keystore,
                    target_tls_keystore_password, target_tls_keystore_type,
                    target_tls_truststore, target_tls_truststore_password, target_tls_truststore_type
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    target_insecure_trust_all = ?, target_verify_hostname = ?, target_rewrite_host_header = ?,
                    listener_tls_cert = ?, listener_tls_key = ?, listener_tls_keystore = ?,
                    listener_tls_keystore_password = ?, listener_tls_keystore_type = ?,
                    listener_tls_truststore = ?, listener_tls_truststore_password = ?, listener_tls_truststore_type = ?,
                    target_tls_cert = ?, target_tls_key = ?, target_tls_keystore = ?,
                    target_tls_keystore_password = ?, target_tls_keystore_type = ?,
                    target_tls_truststore = ?, target_tls_truststore_password = ?, target_tls_truststore_type = ?
                where id = ?
                """)) {
            statement.setString(1, route.listener().host());
            statement.setInt(2, route.listener().port());
            statement.setString(3, route.listener().transportMode().name());
            statement.setString(4, route.listener().clientAuthMode().name());
            statement.setString(5, route.target().host());
            statement.setInt(6, route.target().port());
            statement.setString(7, route.target().transportMode().name());
            setNullableString(statement, 8, route.target().sniHost());
            statement.setInt(9, route.target().insecureTrustAll() ? 1 : 0);
            statement.setInt(10, route.target().verifyHostname() ? 1 : 0);
            statement.setInt(11, route.target().rewriteHostHeader() ? 1 : 0);
            setTlsMaterialParams(statement, 12, route.listener().tlsMaterial());
            setTlsMaterialParams(statement, 20, route.target().tlsMaterial());
            statement.setString(28, route.id());
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
        setNullableString(statement, 9, route.target().sniHost());
        statement.setInt(10, route.target().insecureTrustAll() ? 1 : 0);
        statement.setInt(11, route.target().verifyHostname() ? 1 : 0);
        statement.setInt(12, route.target().rewriteHostHeader() ? 1 : 0);
        setTlsMaterialParams(statement, 13, route.listener().tlsMaterial());
        setTlsMaterialParams(statement, 21, route.target().tlsMaterial());
    }

    private static void setNullableString(PreparedStatement st, int index, String value) throws SQLException {
        if (value != null) {
            st.setString(index, value);
        } else {
            st.setNull(index, Types.VARCHAR);
        }
    }

    private static void setNullablePath(PreparedStatement st, int index, Path path) throws SQLException {
        if (path != null) {
            st.setString(index, path.toString());
        } else {
            st.setNull(index, Types.VARCHAR);
        }
    }

    private void setTlsMaterialParams(PreparedStatement st, int offset, TlsMaterial tls) throws SQLException {
        if (tls == null) {
            for (int i = 0; i < 8; i++) {
                st.setNull(offset + i, Types.VARCHAR);
            }
            return;
        }
        setNullablePath(st, offset,     tls.certificateFile());
        setNullablePath(st, offset + 1, tls.privateKeyFile());
        setNullablePath(st, offset + 2, tls.keyStoreFile());
        setNullableString(st, offset + 3, encryptPassword(tls.keyStorePassword()));
        setNullableString(st, offset + 4, tls.keyStoreType());
        setNullablePath(st, offset + 5, tls.trustStoreFile());
        setNullableString(st, offset + 6, encryptPassword(tls.trustStorePassword()));
        setNullableString(st, offset + 7, tls.trustStoreType());
    }

    private String encryptPassword(String password) {
        return passwordEncryptor.encrypt(password);
    }

    private String decryptPassword(String value) {
        return passwordEncryptor.decrypt(value);
    }

    private RouteConfig mapRoute(ResultSet resultSet) throws SQLException {
        ListenerConfig listener = new ListenerConfig(
                resultSet.getString("listener_host"),
                resultSet.getInt("listener_port"),
                TransportMode.valueOf(resultSet.getString("listener_transport")),
                ClientAuthMode.valueOf(resultSet.getString("listener_client_auth")),
                readTlsMaterial(resultSet, "listener"));
        TargetConfig target = new TargetConfig(
                resultSet.getString("target_host"),
                resultSet.getInt("target_port"),
                TransportMode.valueOf(resultSet.getString("target_transport")),
                resultSet.getString("target_sni_host"),
                resultSet.getInt("target_insecure_trust_all") == 1,
                resultSet.getInt("target_verify_hostname") == 1,
                resultSet.getInt("target_rewrite_host_header") == 1,
                readTlsMaterial(resultSet, "target"));
        return new RouteConfig(resultSet.getString("id"), listener, target);
    }

    private TlsMaterial readTlsMaterial(ResultSet rs, String prefix) throws SQLException {
        String cert = rs.getString(prefix + "_tls_cert");
        String key = rs.getString(prefix + "_tls_key");
        String keystore = rs.getString(prefix + "_tls_keystore");
        String keystorePassword = decryptPassword(rs.getString(prefix + "_tls_keystore_password"));
        String keystoreType = rs.getString(prefix + "_tls_keystore_type");
        String truststore = rs.getString(prefix + "_tls_truststore");
        String truststorePassword = decryptPassword(rs.getString(prefix + "_tls_truststore_password"));
        String truststoreType = rs.getString(prefix + "_tls_truststore_type");
        return new TlsMaterial(
                cert != null && !cert.isBlank() ? Path.of(cert) : null,
                key != null && !key.isBlank() ? Path.of(key) : null,
                keystore != null && !keystore.isBlank() ? Path.of(keystore) : null,
                keystorePassword,
                truststore != null && !truststore.isBlank() ? Path.of(truststore) : null,
                truststorePassword,
                keystoreType != null ? keystoreType : "PKCS12",
                truststoreType != null ? truststoreType : "PKCS12");
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new UncheckedIOException(new IOException("Unable to close SQLite session store", exception));
            }
        }
    }

    private static final class SessionWriterThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Thread.ofPlatform().name("session-store-writer", 0).unstarted(runnable);
            thread.setDaemon(true);
            return thread;
        }
    }
}
