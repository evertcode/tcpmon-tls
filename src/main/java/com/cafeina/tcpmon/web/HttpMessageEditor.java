package com.cafeina.tcpmon.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class HttpMessageEditor {
    private HttpMessageEditor() {
    }

    static byte[] buildHttpMessage(JsonNode httpNode) {
        String startLine = buildStartLine(httpNode);
        String headersText = httpNode.path("headersText").asText("");
        String bodyText = httpNode.path("bodyText").asText("");
        if (startLine.isBlank()) {
            throw new IllegalArgumentException("HTTP start line is required");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : headersText.split("\\r?\\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            headers.put(
                    line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim());
        }

        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        headers.remove("transfer-encoding");
        headers.remove("content-encoding");
        if (body.length > 0 || headers.containsKey("content-length")) {
            headers.put("content-length", Integer.toString(body.length));
        }

        StringBuilder builder = new StringBuilder();
        builder.append(startLine).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(formatHeaderName(entry.getKey())).append(": ").append(entry.getValue()).append("\r\n");
        }
        builder.append("\r\n");

        byte[] head = builder.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = new byte[head.length + body.length];
        System.arraycopy(head, 0, payload, 0, head.length);
        System.arraycopy(body, 0, payload, head.length, body.length);
        return payload;
    }

    private static String buildStartLine(JsonNode httpNode) {
        if (httpNode.hasNonNull("method") || httpNode.hasNonNull("path") || httpNode.hasNonNull("version")) {
            String method = httpNode.path("method").asText("").trim();
            String path = httpNode.path("path").asText("/").trim();
            String query = httpNode.path("query").asText("").trim();
            String version = httpNode.path("version").asText("HTTP/1.1").trim();
            if (method.isBlank()) {
                throw new IllegalArgumentException("HTTP method is required");
            }
            if (path.isBlank()) {
                path = "/";
            }
            String target = query.isBlank() ? path : path + "?" + query;
            return method + " " + target + " " + version;
        }
        return httpNode.path("startLine").asText("").trim();
    }

    private static String formatHeaderName(String normalized) {
        String[] parts = normalized.split("-");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                continue;
            }
            if (index > 0) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
