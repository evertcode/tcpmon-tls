package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.util.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void releasesPendingPayloadWithEditedContent() throws Exception {
        SessionStore store = new SessionStore(tempDir, JsonSupport.objectMapper());
        String sessionId = store.openSession("client", "listener", "target");
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
