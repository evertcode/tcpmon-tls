package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.util.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void releasesPendingPayloadWithEditedContent() throws Exception {
        SessionStore store = new SessionStore(tempDir, JsonSupport.objectMapper());
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
}
