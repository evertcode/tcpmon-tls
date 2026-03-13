package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.session.SessionEvent;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SessionPayloadAggregator {
    private SessionPayloadAggregator() {
    }

    static Map<String, Object> aggregate(List<SessionEvent> events, Direction direction) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        Instant latestTimestamp = null;
        int chunkCount = 0;

        for (SessionEvent event : events) {
            if (!"PAYLOAD".equals(event.type()) || event.direction() != direction) {
                continue;
            }
            Object base64 = event.details().get("base64");
            if (base64 instanceof String encoded) {
                buffer.writeBytes(Base64.getDecoder().decode(encoded));
                latestTimestamp = event.timestamp();
                chunkCount++;
            }
        }

        if (chunkCount == 0) {
            return null;
        }

        byte[] payload = buffer.toByteArray();
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("eventId", "aggregate-" + direction.name().toLowerCase());
        aggregated.put("type", "PAYLOAD");
        aggregated.put("timestamp", latestTimestamp);
        aggregated.put("direction", direction);
        aggregated.put("size", payload.length);
        aggregated.put("chunkCount", chunkCount);
        aggregated.put("base64", Base64.getEncoder().encodeToString(payload));
        aggregated.put("decoded", PayloadInspector.inspectBytes(payload));
        return aggregated;
    }

    static List<Map<String, Object>> aggregateMessages(List<SessionEvent> events, Direction direction) {
        Map<String, Object> aggregated = aggregate(events, direction);
        if (aggregated == null) {
            return List.of();
        }
        Object base64 = aggregated.get("base64");
        if (!(base64 instanceof String encoded)) {
            return List.of(aggregated);
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        List<byte[]> messages = HttpMessageParser.splitMessages(bytes);
        if (messages.size() <= 1) {
            return List.of(aggregated);
        }

        int count = 0;
        java.util.List<Map<String, Object>> output = new java.util.ArrayList<>();
        for (byte[] message : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventId", aggregated.get("eventId") + "-" + (++count));
            entry.put("type", "PAYLOAD");
            entry.put("timestamp", aggregated.get("timestamp"));
            entry.put("direction", direction);
            entry.put("size", message.length);
            entry.put("chunkCount", aggregated.get("chunkCount"));
            entry.put("base64", Base64.getEncoder().encodeToString(message));
            entry.put("decoded", PayloadInspector.inspectBytes(message));
            output.add(entry);
        }
        return Collections.unmodifiableList(output);
    }
}
