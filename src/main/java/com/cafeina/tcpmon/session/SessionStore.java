package com.cafeina.tcpmon.session;

import com.cafeina.tcpmon.Direction;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class SessionStore implements AutoCloseable {
    private final Path rootDir;
    private final Path blobsDir;
    private final Path eventsLog;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingPayload> pendingPayloads = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public SessionStore(Path rootDir, ObjectMapper objectMapper) throws IOException {
        this.rootDir = rootDir;
        this.blobsDir = rootDir.resolve("blobs");
        this.eventsLog = rootDir.resolve("sessions.jsonl");
        this.objectMapper = objectMapper;
        Files.createDirectories(this.blobsDir);
        Files.createDirectories(this.rootDir);
        if (Files.notExists(eventsLog)) {
            Files.createFile(eventsLog);
        }
    }

    public String openSession(String clientAddress, String listenerAddress, String targetAddress) {
        String sessionId = "s-" + ids.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        SessionRecord record = new SessionRecord(sessionId, Instant.now(), clientAddress, listenerAddress, targetAddress);
        sessions.put(sessionId, record);
        appendLog(Map.of(
                "kind", "session-open",
                "sessionId", sessionId,
                "clientAddress", clientAddress,
                "listenerAddress", listenerAddress,
                "targetAddress", targetAddress,
                "timestamp", Instant.now()));
        return sessionId;
    }

    public void closeSession(String sessionId, String status) {
        SessionRecord record = requireSession(sessionId);
        record.markClosed(status, Instant.now());
        appendLog(Map.of(
                "kind", "session-close",
                "sessionId", sessionId,
                "status", status,
                "timestamp", Instant.now()));
    }

    public void recordLifecycle(String sessionId, String type, Map<String, Object> details) {
        SessionRecord record = requireSession(sessionId);
        SessionEvent event = new SessionEvent(nextEventId(), type, Instant.now(), null, 0, null, null, null, null, details);
        record.addEvent(event);
        appendLog(Map.of(
                "kind", "event",
                "sessionId", sessionId,
                "event", event));
    }

    public void recordTls(String sessionId, boolean inbound, Map<String, Object> details) {
        SessionRecord record = requireSession(sessionId);
        if (inbound) {
            record.putInboundTls(details);
        } else {
            record.putOutboundTls(details);
        }
        recordLifecycle(sessionId, inbound ? "TLS_INBOUND" : "TLS_OUTBOUND", details);
    }

    public SessionEvent recordPayload(String sessionId, Direction direction, byte[] payload, String pendingId, Map<String, Object> extraDetails) {
        SessionRecord record = requireSession(sessionId);
        String eventId = nextEventId();
        String payloadPath = writePayload(sessionId, eventId, payload);
        Map<String, Object> details = new LinkedHashMap<>(extraDetails);
        details.put("base64", Base64.getEncoder().encodeToString(payload));
        SessionEvent event = new SessionEvent(
                eventId,
                "PAYLOAD",
                Instant.now(),
                direction,
                payload.length,
                payloadPath,
                previewUtf8(payload),
                previewHex(payload),
                pendingId,
                details);
        record.addEvent(event);
        appendLog(Map.of(
                "kind", "event",
                "sessionId", sessionId,
                "event", event));
        return event;
    }

    public PendingPayload addPending(String sessionId, Direction direction, byte[] payload, Consumer<byte[]> forwarder) {
        String pendingId = "p-" + ids.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        PendingPayload pendingPayload = new PendingPayload(pendingId, sessionId, direction, Instant.now(), payload, forwarder);
        pendingPayloads.put(pendingId, pendingPayload);
        requireSession(sessionId).addPending(pendingPayload);
        return pendingPayload;
    }

    public boolean releasePending(String pendingId, byte[] replacementPayload) {
        PendingPayload pendingPayload = pendingPayloads.remove(pendingId);
        if (pendingPayload == null) {
            return false;
        }
        requireSession(pendingPayload.sessionId()).removePending(pendingId);
        byte[] finalPayload = replacementPayload == null ? pendingPayload.originalBytes() : replacementPayload;
        pendingPayload.forwarder().accept(finalPayload);
        recordLifecycle(pendingPayload.sessionId(), "PENDING_RELEASED", Map.of(
                "pendingId", pendingId,
                "edited", replacementPayload != null,
                "finalSize", finalPayload.length));
        return true;
    }

    public List<Map<String, Object>> listSessions() {
        return sessions.values().stream()
                .map(SessionRecord::summary)
                .sorted((left, right) -> right.get("startedAt").toString().compareTo(left.get("startedAt").toString()))
                .toList();
    }

    public Map<String, Object> sessionDetails(String sessionId) {
        SessionRecord record = sessions.get(sessionId);
        return record == null ? null : record.snapshot();
    }

    public SessionEvent findEvent(String sessionId, String eventId) {
        SessionRecord record = sessions.get(sessionId);
        return record == null ? null : record.eventById(eventId);
    }

    public List<PendingPayload> pendingPayloads(String sessionId) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null) {
            return List.of();
        }
        Object pending = record.snapshot().get("pendingPayloads");
        if (pending instanceof List<?> list) {
            List<PendingPayload> items = new ArrayList<>();
            for (Object candidate : list) {
                if (candidate instanceof PendingPayload payload) {
                    items.add(payload);
                }
            }
            return items;
        }
        return List.of();
    }

    private SessionRecord requireSession(String sessionId) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }
        return record;
    }

    private String nextEventId() {
        return "e-" + ids.incrementAndGet();
    }

    private String writePayload(String sessionId, String eventId, byte[] payload) {
        try {
            Path sessionDir = blobsDir.resolve(sessionId);
            Files.createDirectories(sessionDir);
            Path output = sessionDir.resolve(eventId + ".bin");
            try (OutputStream stream = Files.newOutputStream(output)) {
                stream.write(payload);
            }
            return rootDir.relativize(output).toString();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void appendLog(Map<String, Object> entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            Files.writeString(eventsLog, json + System.lineSeparator(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String previewUtf8(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ".");
        return text.substring(0, Math.min(120, text.length()));
    }

    private static String previewHex(byte[] payload) {
        int limit = Math.min(payload.length, 48);
        StringBuilder builder = new StringBuilder(limit * 3);
        for (int index = 0; index < limit; index++) {
            builder.append(String.format("%02x", payload[index]));
            if (index < limit - 1) {
                builder.append(' ');
            }
        }
        if (payload.length > limit) {
            builder.append(" ...");
        }
        return builder.toString();
    }

    @Override
    public void close() {
    }
}
