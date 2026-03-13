package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.session.SessionEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PayloadInspector {
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");

    private PayloadInspector() {
    }

    static Map<String, Object> inspect(SessionEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("type", event.type());
        payload.put("timestamp", event.timestamp());
        payload.put("direction", event.direction());
        payload.put("size", event.size());
        payload.put("payloadPath", event.payloadPath());
        payload.put("previewText", event.previewText());
        payload.put("previewHex", event.previewHex());
        payload.put("pendingId", event.pendingId());
        payload.put("details", event.details());

        if (!"PAYLOAD".equals(event.type())) {
            return payload;
        }

        Object encoded = event.details().get("base64");
        if (!(encoded instanceof String base64)) {
            return payload;
        }

        byte[] bytes = Base64.getDecoder().decode(base64);
        payload.put("decoded", inspectBytes(bytes));
        return payload;
    }

    static Map<String, Object> inspectBytes(byte[] bytes) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        String rawText = new String(bytes, StandardCharsets.UTF_8);
        decoded.put("rawText", sanitize(rawText));
        decoded.put("bodyText", sanitize(rawText));
        decoded.put("isHttp", false);

        String latin1 = new String(bytes, StandardCharsets.ISO_8859_1);
        int headerEnd = latin1.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return decoded;
        }

        String headersBlock = latin1.substring(0, headerEnd);
        String[] lines = headersBlock.split("\r\n");
        if (lines.length == 0 || !looksLikeHttp(lines[0])) {
            return decoded;
        }

        List<Map<String, String>> headers = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.add(Map.of(
                        "name", line.substring(0, separator).trim(),
                        "value", line.substring(separator + 1).trim()));
            }
        }

        byte[] bodyBytes = latin1.substring(headerEnd + 4).getBytes(StandardCharsets.ISO_8859_1);
        decoded.put("isHttp", true);
        decoded.put("startLine", lines[0]);
        decoded.put("headers", headers);
        decoded.put("headersText", headersBlock);
        decoded.put("bodyText", sanitize(new String(bodyBytes, StandardCharsets.UTF_8)));
        decoded.put("bodyBase64", Base64.getEncoder().encodeToString(bodyBytes));
        return decoded;
    }

    private static boolean looksLikeHttp(String startLine) {
        if (startLine.startsWith("HTTP/")) {
            return true;
        }
        int separator = startLine.indexOf(' ');
        if (separator <= 0) {
            return false;
        }
        return HTTP_METHODS.contains(startLine.substring(0, separator));
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ".");
    }
}
