package com.cafeina.tcpmon.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Set;

final class HttpRequestRewriter {
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");

    private HttpRequestRewriter() {
    }

    static RewriteResult rewriteHostHeader(byte[] payload, String targetHost, int targetPort) {
        String body = new String(payload, StandardCharsets.ISO_8859_1);
        int headerEnd = body.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return new RewriteResult(payload, false);
        }

        String headers = body.substring(0, headerEnd);
        String[] lines = headers.split("\r\n", -1);
        if (lines.length == 0 || !looksLikeHttpRequest(lines[0])) {
            return new RewriteResult(payload, false);
        }

        String hostHeaderValue = targetPort == 80 || targetPort == 443
                ? targetHost
                : targetHost + ":" + targetPort;

        StringBuilder rewritten = new StringBuilder(body.length() + 64);
        rewritten.append(lines[0]).append("\r\n");
        boolean hostFound = false;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.regionMatches(true, 0, "Host:", 0, 5)) {
                rewritten.append("Host: ").append(hostHeaderValue).append("\r\n");
                hostFound = true;
            } else if (!line.isEmpty()) {
                rewritten.append(line).append("\r\n");
            }
        }
        if (!hostFound) {
            rewritten.append("Host: ").append(hostHeaderValue).append("\r\n");
        }
        rewritten.append("\r\n").append(body.substring(headerEnd + 4));

        byte[] rewrittenPayload = rewritten.toString().getBytes(StandardCharsets.ISO_8859_1);
        return new RewriteResult(rewrittenPayload, true);
    }

    private static boolean looksLikeHttpRequest(String requestLine) {
        int methodEnd = requestLine.indexOf(' ');
        if (methodEnd <= 0) {
            return false;
        }
        String method = requestLine.substring(0, methodEnd);
        return HTTP_METHODS.contains(method);
    }

    record RewriteResult(byte[] payload, boolean rewritten) {
    }
}
