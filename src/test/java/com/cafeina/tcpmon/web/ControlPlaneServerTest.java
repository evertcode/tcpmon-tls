package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.session.SessionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneServerTest {
    @Test
    void replayIsAllowedOnlyForClientToTargetPayloads() {
        SessionEvent requestPayload = new SessionEvent(
                "e-1", "PAYLOAD", Instant.now(), Direction.CLIENT_TO_TARGET, 10, null, null, null, null, Map.of("base64", "AQ=="));
        SessionEvent responsePayload = new SessionEvent(
                "e-2", "PAYLOAD", Instant.now(), Direction.TARGET_TO_CLIENT, 10, null, null, null, null, Map.of("base64", "AQ=="));
        SessionEvent lifecycle = new SessionEvent(
                "e-3", "TARGET_CONNECTED", Instant.now(), null, 0, null, null, null, null, Map.of());

        assertTrue(ControlPlaneServer.isReplayable(requestPayload));
        assertFalse(ControlPlaneServer.isReplayable(responsePayload));
        assertFalse(ControlPlaneServer.isReplayable(lifecycle));
    }
}
