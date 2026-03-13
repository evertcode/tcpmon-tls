package com.cafeina.tcpmon.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SessionRecord {
    private final String sessionId;
    private final String routeId;
    private final Instant startedAt;
    private final String clientAddress;
    private final String listenerAddress;
    private final String targetAddress;
    private final List<SessionEvent> events = new ArrayList<>();
    private final Map<String, Object> inboundTls = new LinkedHashMap<>();
    private final Map<String, Object> outboundTls = new LinkedHashMap<>();
    private final Map<String, PendingPayload> pendingPayloads = new LinkedHashMap<>();
    private Instant endedAt;
    private String status = "OPEN";

    public SessionRecord(String sessionId, String routeId, Instant startedAt, String clientAddress, String listenerAddress, String targetAddress) {
        this.sessionId = sessionId;
        this.routeId = routeId;
        this.startedAt = startedAt;
        this.clientAddress = clientAddress;
        this.listenerAddress = listenerAddress;
        this.targetAddress = targetAddress;
    }

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized void addEvent(SessionEvent event) {
        events.add(event);
    }

    public synchronized void putInboundTls(Map<String, Object> details) {
        inboundTls.putAll(details);
    }

    public synchronized void putOutboundTls(Map<String, Object> details) {
        outboundTls.putAll(details);
    }

    public synchronized void addPending(PendingPayload payload) {
        pendingPayloads.put(payload.pendingId(), payload);
    }

    public synchronized PendingPayload removePending(String pendingId) {
        return pendingPayloads.remove(pendingId);
    }

    public synchronized void markClosed(String newStatus, Instant finishedAt) {
        this.status = newStatus;
        this.endedAt = finishedAt;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("routeId", routeId);
        payload.put("startedAt", startedAt);
        payload.put("endedAt", endedAt);
        payload.put("status", status);
        payload.put("clientAddress", clientAddress);
        payload.put("listenerAddress", listenerAddress);
        payload.put("targetAddress", targetAddress);
        payload.put("inboundTls", new LinkedHashMap<>(inboundTls));
        payload.put("outboundTls", new LinkedHashMap<>(outboundTls));
        payload.put("events", new ArrayList<>(events));
        payload.put("pendingPayloads", new ArrayList<>(pendingPayloads.values()));
        return payload;
    }

    public synchronized Map<String, Object> summary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("routeId", routeId);
        payload.put("startedAt", startedAt);
        payload.put("endedAt", endedAt);
        payload.put("status", status);
        payload.put("clientAddress", clientAddress);
        payload.put("listenerAddress", listenerAddress);
        payload.put("targetAddress", targetAddress);
        payload.put("eventCount", events.size());
        payload.put("pendingCount", pendingPayloads.size());
        return payload;
    }

    public synchronized SessionEvent eventById(String eventId) {
        return events.stream().filter(event -> event.eventId().equals(eventId)).findFirst().orElse(null);
    }

    public synchronized String routeId() {
        return routeId;
    }
}
