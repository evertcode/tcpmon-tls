package com.cafeina.tcpmon.web;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.BrotliInputStream;
import com.cafeina.tcpmon.session.SessionEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

final class PayloadInspector {
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");
    private static volatile boolean brotliLoaded;

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
        Map<String, String> headerMap = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator > 0) {
                String name = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                headers.add(Map.of("name", name, "value", value));
                headerMap.put(name.toLowerCase(Locale.ROOT), value);
            }
        }

        byte[] wireBodyBytes = latin1.substring(headerEnd + 4).getBytes(StandardCharsets.ISO_8859_1);
        byte[] decodedBodyBytes = decodeBody(wireBodyBytes, headerMap);
        decoded.put("isHttp", true);
        decoded.put("startLine", lines[0]);
        decoded.put("request", parseRequestLine(lines[0]));
        decoded.put("headers", headers);
        decoded.put("headersText", headersBlock);
        decoded.put("bodyText", sanitize(new String(decodedBodyBytes, StandardCharsets.UTF_8)));
        decoded.put("bodyBase64", Base64.getEncoder().encodeToString(decodedBodyBytes));
        decoded.put("wireBodyBase64", Base64.getEncoder().encodeToString(wireBodyBytes));
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

    private static Map<String, Object> parseRequestLine(String startLine) {
        if (startLine.startsWith("HTTP/")) {
            return null;
        }
        String[] parts = startLine.split(" ", 3);
        if (parts.length != 3) {
            return null;
        }
        String target = parts[1];
        int querySeparator = target.indexOf('?');
        String path = querySeparator >= 0 ? target.substring(0, querySeparator) : target;
        String query = querySeparator >= 0 ? target.substring(querySeparator + 1) : "";

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", parts[0]);
        request.put("path", path);
        request.put("query", query);
        request.put("version", parts[2]);
        return request;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", ".");
    }

    private static byte[] decodeBody(byte[] bodyBytes, Map<String, String> headers) {
        byte[] unchunked = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT).contains("chunked")
                ? decodeChunked(bodyBytes)
                : bodyBytes;
        String contentEncoding = headers.getOrDefault("content-encoding", "").toLowerCase(Locale.ROOT);
        return switch (contentEncoding) {
            case "gzip" -> decompress(unchunked, Compression.GZIP);
            case "deflate" -> decompress(unchunked, Compression.DEFLATE);
            case "br" -> decompressBrotli(unchunked);
            default -> unchunked;
        };
    }

    private static byte[] decodeChunked(byte[] bodyBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < bodyBytes.length) {
            int lineEnd = indexOf(bodyBytes, offset, "\r\n".getBytes(StandardCharsets.ISO_8859_1));
            if (lineEnd < 0) {
                return bodyBytes;
            }
            String sizeLine = new String(bodyBytes, offset, lineEnd - offset, StandardCharsets.ISO_8859_1).trim();
            int separator = sizeLine.indexOf(';');
            String sizeToken = separator >= 0 ? sizeLine.substring(0, separator) : sizeLine;
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeToken.trim(), 16);
            } catch (NumberFormatException ignored) {
                return bodyBytes;
            }
            offset = lineEnd + 2;
            if (chunkSize == 0) {
                break;
            }
            if (offset + chunkSize > bodyBytes.length) {
                return bodyBytes;
            }
            output.write(bodyBytes, offset, chunkSize);
            offset += chunkSize + 2;
        }
        return output.toByteArray();
    }

    private static byte[] decompress(byte[] bodyBytes, Compression compression) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bodyBytes);
             java.io.InputStream decompressor = compression == Compression.GZIP
                     ? new GZIPInputStream(input)
                     : new InflaterInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            decompressor.transferTo(output);
            return output.toByteArray();
        } catch (IOException ignored) {
            return bodyBytes;
        }
    }

    private static byte[] decompressBrotli(byte[] bodyBytes) {
        try {
            ensureBrotliAvailable();
        } catch (Throwable ignored) {
            return "[brotli body not decoded: native library unavailable]".getBytes(StandardCharsets.UTF_8);
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(bodyBytes);
             BrotliInputStream brotliInput = new BrotliInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            brotliInput.transferTo(output);
            return output.toByteArray();
        } catch (IOException ignored) {
            return bodyBytes;
        }
    }

    private static void ensureBrotliAvailable() {
        if (!brotliLoaded) {
            synchronized (PayloadInspector.class) {
                if (!brotliLoaded) {
                    Brotli4jLoader.ensureAvailability();
                    brotliLoaded = true;
                }
            }
        }
    }

    private static int indexOf(byte[] haystack, int start, byte[] needle) {
        outer:
        for (int index = start; index <= haystack.length - needle.length; index++) {
            for (int probe = 0; probe < needle.length; probe++) {
                if (haystack[index + probe] != needle[probe]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private enum Compression {
        GZIP,
        DEFLATE
    }
}
