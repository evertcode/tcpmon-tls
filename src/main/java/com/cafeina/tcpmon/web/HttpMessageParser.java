package com.cafeina.tcpmon.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HttpMessageParser {
    private HttpMessageParser() {
    }

    static List<byte[]> splitMessages(byte[] payload) {
        List<byte[]> messages = new ArrayList<>();
        int offset = 0;
        while (offset < payload.length) {
            int next = nextMessageEnd(payload, offset);
            if (next <= offset) {
                break;
            }
            byte[] message = java.util.Arrays.copyOfRange(payload, offset, next);
            @SuppressWarnings("unchecked")
            Map<String, Object> inspected = (Map<String, Object>) PayloadInspector.inspectBytes(message);
            if (!Boolean.TRUE.equals(inspected.get("isHttp"))) {
                break;
            }
            messages.add(message);
            offset = next;
        }
        if (messages.isEmpty()) {
            messages.add(payload);
        }
        return messages;
    }

    private static int nextMessageEnd(byte[] payload, int start) {
        String latin1 = new String(payload, start, payload.length - start, StandardCharsets.ISO_8859_1);
        int headerEndRelative = latin1.indexOf("\r\n\r\n");
        if (headerEndRelative < 0) {
            return payload.length;
        }
        int headerEnd = start + headerEndRelative;
        String headers = latin1.substring(0, headerEndRelative);
        int contentLength = headerValue(headers, "content-length");
        int bodyStart = headerEnd + 4;
        int messageEnd = bodyStart + Math.max(contentLength, 0);
        return Math.min(messageEnd, payload.length);
    }

    private static int headerValue(String headers, String name) {
        String[] lines = headers.split("\r\n");
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String headerName = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (headerName.equals(name)) {
                try {
                    return Integer.parseInt(line.substring(separator + 1).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
