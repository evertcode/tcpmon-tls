package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;

import java.time.Instant;
import java.util.function.Consumer;

public record PendingPayload(
        String pendingId,
        String sessionId,
        Direction direction,
        Instant createdAt,
        byte[] originalBytes,
        Consumer<byte[]> forwarder) {
}
