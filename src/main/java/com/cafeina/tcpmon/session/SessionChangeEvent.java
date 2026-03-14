package com.cafeina.tcpmon.session;

import java.time.Instant;

public record SessionChangeEvent(
        String type,
        String sessionId,
        String routeId,
        Instant timestamp,
        String reason) {
}
