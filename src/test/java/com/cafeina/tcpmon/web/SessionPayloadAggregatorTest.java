package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.session.SessionEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPayloadAggregatorTest {
    @Test
    void aggregatesFragmentedHttpResponseIntoSingleView() {
        SessionEvent firstChunk = payloadEvent(
                "e-1",
                Direction.TARGET_TO_CLIENT,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"userId\":1,".getBytes(StandardCharsets.ISO_8859_1));
        SessionEvent secondChunk = payloadEvent(
                "e-2",
                Direction.TARGET_TO_CLIENT,
                "\"title\":\"hello\"}".getBytes(StandardCharsets.ISO_8859_1));

        Map<String, Object> aggregated = SessionPayloadAggregator.aggregate(List.of(firstChunk, secondChunk), Direction.TARGET_TO_CLIENT);

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = (Map<String, Object>) aggregated.get("decoded");
        assertEquals(2, aggregated.get("chunkCount"));
        assertTrue((Boolean) decoded.get("isHttp"));
        assertEquals("HTTP/1.1 200 OK", decoded.get("startLine"));
        assertEquals("{\"userId\":1,\"title\":\"hello\"}", decoded.get("bodyText"));
    }

    private static SessionEvent payloadEvent(String eventId, Direction direction, byte[] payload) {
        return new SessionEvent(
                eventId,
                "PAYLOAD",
                Instant.now(),
                direction,
                payload.length,
                null,
                null,
                null,
                null,
                Map.of("base64", Base64.getEncoder().encodeToString(payload)));
    }
}
