package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.util.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void releasesPendingPayloadWithEditedContent() throws Exception {
        try (SessionStore store = new SessionStore(tempDir, JsonSupport.objectMapper())) {
            String sessionId = store.openSession("default", "client", "listener", "target");
            AtomicReference<byte[]> forwarded = new AtomicReference<>();

            PendingPayload pending = store.addPending(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    "original".getBytes(StandardCharsets.UTF_8),
                    forwarded::set);

            boolean released = store.releasePending(pending.pendingId(), "edited".getBytes(StandardCharsets.UTF_8));

            assertTrue(released);
            assertEquals("edited", new String(forwarded.get(), StandardCharsets.UTF_8));
            assertTrue(((java.util.List<?>) store.sessionDetails(sessionId).get("pendingPayloads")).isEmpty());
        }
    }

    @Test
    void reloadsPersistedSessionsAcrossStoreRestart() throws Exception {
        Path storeDir = tempDir.resolve("sqlite-sessions");
        String sessionId;
        try (SessionStore store = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            sessionId = store.openSession("route-a", "client-a", "listener-a", "target-a");
            store.recordLifecycle(sessionId, "CLIENT_CONNECTED", Map.of("source", "test"));
            store.recordPayload(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    "GET /health HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8),
                    null,
                    Map.of("intercepted", false));
            store.closeSession(sessionId, "CLOSED");
        }

        try (SessionStore reloaded = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            assertEquals(1, reloaded.listSessions().size());
            Map<String, Object> summary = reloaded.listSessions().getFirst();
            assertEquals("route-a", summary.get("routeId"));
            assertEquals("listener-a", summary.get("listenerAddress"));

            Map<String, Object> details = reloaded.sessionDetails(sessionId);
            assertNotNull(details);
            assertEquals("CLOSED", details.get("status"));
            var events = (java.util.List<?>) details.get("events");
            assertEquals(2, events.size());
            SessionEvent payloadEvent = (SessionEvent) events.get(1);
            assertEquals(
                    "GET /health HTTP/1.1\r\n\r\n",
                    new String(Base64.getDecoder().decode(payloadEvent.details().get("base64").toString()), StandardCharsets.UTF_8));
        }
    }

    @Test
    void publishesSessionChangeEventsForLifecycleOperations() throws Exception {
        try (SessionStore store = new SessionStore(tempDir.resolve("publisher-sessions"), JsonSupport.objectMapper())) {
            List<SessionChangeEvent> changes = new CopyOnWriteArrayList<>();
            store.addChangeListener(changes::add);

            String sessionId = store.openSession("route-a", "client-a", "listener-a", "target-a");
            store.recordLifecycle(sessionId, "CLIENT_CONNECTED", Map.of("source", "test"));
            store.recordPayload(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    "GET /health HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8),
                    null,
                    Map.of("intercepted", false));
            store.closeSession(sessionId, "CLOSED");

            assertEquals(List.of(
                    "session-created",
                    "session-updated",
                    "session-updated",
                    "session-closed"),
                    changes.stream().map(SessionChangeEvent::type).toList());
            assertTrue(changes.stream().allMatch(change -> sessionId.equals(change.sessionId())));
            assertTrue(changes.stream().allMatch(change -> "route-a".equals(change.routeId())));
        }
    }

    @Test
    void persistsExchangeSummariesForCapturedHttpRequests() throws Exception {
        Path storeDir = tempDir.resolve("exchange-summaries");
        String sessionId;
        try (SessionStore store = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            sessionId = store.openSession("route-a", "client-a", "listener-a", "target-a");
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET, (
                    "GET /one HTTP/1.1\r\nHost: example.com\r\n\r\n" +
                    "POST /two?x=1 HTTP/1.1\r\nHost: example.com\r\nContent-Length: 0\r\n\r\n"
            ).getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, (
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK" +
                    "HTTP/1.1 201 Created\r\nContent-Length: 3\r\n\r\nYES"
            ).getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
        }

        try (SessionStore reloaded = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            List<Map<String, Object>> rows = reloaded.listRequestRows();

            assertEquals(2, rows.size());
            assertEquals("/one", rows.get(0).get("requestPath"));
            assertEquals("GET", rows.get(0).get("requestMethod"));
            assertEquals("200", rows.get(0).get("responseStatusCode"));
            assertEquals("/two", rows.get(1).get("requestPath"));
            assertEquals("POST", rows.get(1).get("requestMethod"));
            assertEquals("201", rows.get(1).get("responseStatusCode"));
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + storeDir.resolve("sessions.db").toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet count = statement.executeQuery("select count(*) from session_exchanges where session_id = '" + sessionId + "'")) {
            assertTrue(count.next());
            assertEquals(2, count.getInt(1));
        }
    }

    @Test
    void backfillsMissingExchangeSummariesFromStoredPayloadEvents() throws Exception {
        Path storeDir = tempDir.resolve("exchange-backfill");
        String sessionId;
        try (SessionStore store = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            sessionId = store.openSession("route-a", "client-a", "listener-a", "target-a");
            store.recordPayload(
                    sessionId,
                    Direction.CLIENT_TO_TARGET,
                    "GET /legacy HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1),
                    null,
                    Map.of());
            store.recordPayload(
                    sessionId,
                    Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1),
                    null,
                    Map.of());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + storeDir.resolve("sessions.db").toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from session_exchanges where session_id = '" + sessionId + "'");
        }

        try (SessionStore reloaded = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            List<Map<String, Object>> rows = reloaded.listRequestRows();

            assertEquals(1, rows.size());
            assertEquals("/legacy", rows.getFirst().get("requestPath"));
            assertEquals("204", rows.getFirst().get("responseStatusCode"));
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + storeDir.resolve("sessions.db").toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet count = statement.executeQuery("select count(*) from session_exchanges where session_id = '" + sessionId + "'")) {
            assertTrue(count.next());
            assertEquals(1, count.getInt(1));
        }
    }

    @Test
    void listRequestRowsPaginatedRespectsLimitAndReturnsCursor() throws Exception {
        try (SessionStore store = new SessionStore(tempDir.resolve("paginate-test"), JsonSupport.objectMapper())) {
            for (int i = 0; i < 5; i++) {
                String sid = store.openSession("route-p", "client", "listener", "target");
                store.recordPayload(sid, Direction.CLIENT_TO_TARGET,
                        ("GET /item" + i + " HTTP/1.1\r\nHost: example.com\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1),
                        null, Map.of());
                store.recordPayload(sid, Direction.TARGET_TO_CLIENT,
                        "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1),
                        null, Map.of());
                store.closeSession(sid, "CLOSED");
            }

            Map<String, Object> page1 = store.listRequestRowsPaginated("route-p", 3, null, null, null, null);
            List<?> rows1 = (List<?>) page1.get("requests");
            assertEquals(3, rows1.size());
            assertTrue((Boolean) page1.get("hasMore"));
            assertNotNull(page1.get("nextCursor"));

            String cursor = (String) page1.get("nextCursor");
            Map<String, Object> page2 = store.listRequestRowsPaginated("route-p", 3, cursor, null, null, null);
            List<?> rows2 = (List<?>) page2.get("requests");
            assertEquals(2, rows2.size());
            assertFalse((Boolean) page2.get("hasMore"));
            assertNull(page2.get("nextCursor"));
        }
    }

    @Test
    void listRequestRowsPaginatedFiltersCorrectly() throws Exception {
        try (SessionStore store = new SessionStore(tempDir.resolve("filter-test"), JsonSupport.objectMapper())) {
            String s1 = store.openSession("route-f", "c", "l", "t");
            store.recordPayload(s1, Direction.CLIENT_TO_TARGET,
                    "GET /alpha HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(s1, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());

            String s2 = store.openSession("route-f", "c", "l", "t");
            store.recordPayload(s2, Direction.CLIENT_TO_TARGET,
                    "POST /beta HTTP/1.1\r\nHost: h\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(s2, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());

            Map<String, Object> byMethod = store.listRequestRowsPaginated("route-f", 10, null, "POST", null, null);
            assertEquals(1, ((List<?>) byMethod.get("requests")).size());

            Map<String, Object> byStatus = store.listRequestRowsPaginated("route-f", 10, null, null, "404", null);
            assertEquals(1, ((List<?>) byStatus.get("requests")).size());

            Map<String, Object> bySearch = store.listRequestRowsPaginated("route-f", 10, null, null, null, "alpha");
            assertEquals(1, ((List<?>) bySearch.get("requests")).size());

            Map<String, Object> noMatch = store.listRequestRowsPaginated("route-f", 10, null, null, null, "zzz");
            assertEquals(0, ((List<?>) noMatch.get("requests")).size());
        }
    }

    @Test
    void requestFacetsReturnsCorrectMethodsAndStatusCodes() throws Exception {
        try (SessionStore store = new SessionStore(tempDir.resolve("facets-test"), JsonSupport.objectMapper())) {
            String s1 = store.openSession("route-x", "c", "l", "t");
            store.recordPayload(s1, Direction.CLIENT_TO_TARGET,
                    "GET /a HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(s1, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());

            String s2 = store.openSession("route-x", "c", "l", "t");
            store.recordPayload(s2, Direction.CLIENT_TO_TARGET,
                    "POST /b HTTP/1.1\r\nHost: h\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(s2, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 500 Error\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());

            Map<String, Object> facets = store.requestFacets("route-x");
            List<?> methods = (List<?>) facets.get("methods");
            List<?> statusCodes = (List<?>) facets.get("statusCodes");
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertTrue(statusCodes.contains("200"));
            assertTrue(statusCodes.contains("500"));
            assertEquals(2L, facets.get("totalRequests"));
        }
    }

    @Test
    void listSessionsOmitsBodyDataFromSummaryRows() throws Exception {
        try (SessionStore store = new SessionStore(tempDir.resolve("no-body-test"), JsonSupport.objectMapper())) {
            String sid = store.openSession("route-b", "c", "l", "t");
            store.recordPayload(sid, Direction.CLIENT_TO_TARGET,
                    "GET /check HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1),
                    null, Map.of());
            store.recordPayload(sid, Direction.TARGET_TO_CLIENT,
                    ("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello").getBytes(StandardCharsets.ISO_8859_1),
                    null, Map.of());

            List<Map<String, Object>> sessions = store.listSessions();
            assertEquals(1, sessions.size());
            Map<String, Object> summary = sessions.getFirst();
            assertFalse(summary.containsKey("base64"), "listSessions must not include raw payload base64");
            assertFalse(summary.containsKey("bodyText"), "listSessions must not include decoded body text");
        }
    }

    @Test
    void backfillRunsOnceAndDoesNotRepeatOnSubsequentLoads() throws Exception {
        Path storeDir = tempDir.resolve("backfill-once");
        String sessionId;
        try (SessionStore store = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            sessionId = store.openSession("route-c", "c", "l", "t");
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET,
                    "GET /old HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), null, Map.of());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + storeDir.resolve("sessions.db").toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from session_exchanges where session_id = '" + sessionId + "'");
        }

        try (SessionStore reload1 = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            List<Map<String, Object>> rows = reload1.listSessions();
            assertEquals(1, rows.size());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + storeDir.resolve("sessions.db").toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet count = statement.executeQuery("select count(*) from session_exchanges where session_id = '" + sessionId + "'")) {
            assertTrue(count.next());
            assertEquals(1, count.getInt(1), "backfill should have repopulated the exchange row");
        }

        try (SessionStore reload2 = new SessionStore(storeDir, JsonSupport.objectMapper())) {
            List<Map<String, Object>> rows = reload2.listSessions();
            assertEquals(1, rows.size());
            assertNotNull(rows.getFirst().get("requestPath"), "requestPath should be present after backfill");
        }
    }

    @Test
    void migratesLegacySchemaAndSetsUserVersion() throws Exception {
        Path storeDir = tempDir.resolve("legacy-schema");
        Path dbPath = storeDir.resolve("sessions.db");
        java.nio.file.Files.createDirectories(storeDir);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table routes (
                        id text primary key,
                        listener_host text not null default '0.0.0.0',
                        listener_port integer not null,
                        listener_transport text not null default 'PLAIN',
                        listener_client_auth text not null default 'NONE',
                        target_host text not null,
                        target_port integer not null,
                        target_transport text not null default 'PLAIN',
                        target_sni_host text,
                        target_insecure_trust_all integer not null default 0,
                        target_verify_hostname integer not null default 1,
                        target_rewrite_host_header integer not null default 0
                    )
                    """);
            statement.execute("""
                    create table sessions (
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
                    create table session_events (
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
                        primary key (session_id, event_id)
                    )
                    """);
        }

        try (SessionStore ignored = new SessionStore(storeDir, JsonSupport.objectMapper());
             var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("pragma user_version")) {
                assertTrue(version.next());
                assertEquals(3, version.getInt(1));
            }
            try (ResultSet columns = statement.executeQuery("pragma table_info(routes)")) {
                List<String> names = new java.util.ArrayList<>();
                while (columns.next()) {
                    names.add(columns.getString("name"));
                }
                assertTrue(names.contains("listener_tls_cert"));
                assertTrue(names.contains("target_tls_truststore_password"));
            }
            try (ResultSet exchangeTable = statement.executeQuery("""
                    select 1
                    from sqlite_master
                    where type = 'table'
                      and name = 'session_exchanges'
                    """)) {
                assertTrue(exchangeTable.next());
            }
        }
    }
}
