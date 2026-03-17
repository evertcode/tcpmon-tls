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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                assertEquals(2, version.getInt(1));
            }
            try (ResultSet columns = statement.executeQuery("pragma table_info(routes)")) {
                List<String> names = new java.util.ArrayList<>();
                while (columns.next()) {
                    names.add(columns.getString("name"));
                }
                assertTrue(names.contains("listener_tls_cert"));
                assertTrue(names.contains("target_tls_truststore_password"));
            }
        }
    }
}
