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
        return aggregateMessages(events, direction, false);
    }

    static List<Map<String, Object>> aggregateMessages(List<SessionEvent> events, Direction direction, boolean fullBody) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.util.List<Integer> chunkOffsets = new java.util.ArrayList<>();
        java.util.List<Instant> chunkTimestamps = new java.util.ArrayList<>();
        Instant latestTimestamp = null;
        int chunkCount = 0;

        for (SessionEvent event : events) {
            if (!"PAYLOAD".equals(event.type()) || event.direction() != direction) {
                continue;
            }
            Object base64 = event.details().get("base64");
            if (base64 instanceof String encoded) {
                byte[] chunk = Base64.getDecoder().decode(encoded);
                chunkOffsets.add(buffer.size());
                chunkTimestamps.add(event.timestamp());
                buffer.writeBytes(chunk);
                latestTimestamp = event.timestamp();
                chunkCount++;
            }
        }

        if (chunkCount == 0) {
            return List.of();
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
        aggregated.put("decoded", PayloadInspector.inspectBytes(payload, fullBody));

        List<byte[]> messages = HttpMessageParser.splitMessages(payload);
        if (messages.size() <= 1) {
            return List.of(aggregated);
        }

        int msgOffset = 0;
        int count = 0;
        java.util.List<Map<String, Object>> output = new java.util.ArrayList<>();
        for (byte[] message : messages) {
            Instant msgTimestamp = timestampAtOffset(msgOffset, chunkOffsets, chunkTimestamps, latestTimestamp);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventId", aggregated.get("eventId") + "-" + (++count));
            entry.put("type", "PAYLOAD");
            entry.put("timestamp", msgTimestamp);
            entry.put("direction", direction);
            entry.put("size", message.length);
            entry.put("chunkCount", aggregated.get("chunkCount"));
            entry.put("base64", Base64.getEncoder().encodeToString(message));
            entry.put("decoded", PayloadInspector.inspectBytes(message, fullBody));
            output.add(entry);
            msgOffset += message.length;
        }
        return Collections.unmodifiableList(output);
    }

    private static Instant timestampAtOffset(int offset, java.util.List<Integer> chunkOffsets,
            java.util.List<Instant> chunkTimestamps, Instant fallback) {
        Instant result = fallback;
        for (int i = 0; i < chunkOffsets.size(); i++) {
            if (chunkOffsets.get(i) <= offset) {
                result = chunkTimestamps.get(i);
            } else {
                break;
            }
        }
        return result;
    }
}
