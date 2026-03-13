package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;

import java.time.Instant;
import java.util.Map;

public record SessionEvent(
        String eventId,
        String type,
        Instant timestamp,
        Direction direction,
        int size,
        String payloadPath,
        String previewText,
        String previewHex,
        String pendingId,
        Map<String, Object> details) {
}
