package com.cafeina.tcpmon.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadInspectorTest {
    @Test
    void parsesHttpRequestHeadersAndBody() {
        byte[] payload = ("POST /api/items HTTP/1.1\r\n"
                + "Host: example.com\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + "{\"ok\":true}").getBytes(StandardCharsets.ISO_8859_1);

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertTrue((Boolean) decoded.get("isHttp"));
        assertEquals("POST /api/items HTTP/1.1", decoded.get("startLine"));
        assertEquals("{\"ok\":true}", decoded.get("bodyText"));
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) decoded.get("request");
        assertEquals("POST", request.get("method"));
        assertEquals("/api/items", request.get("path"));
        assertEquals("", request.get("query"));
        assertEquals("HTTP/1.1", request.get("version"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> headers = (List<Map<String, String>>) decoded.get("headers");
        assertEquals("Host", headers.getFirst().get("name"));
        assertEquals("example.com", headers.getFirst().get("value"));
    }

    @Test
    void parsesRequestQueryFromStartLine() {
        byte[] payload = ("GET /search?q=test&page=2 HTTP/1.1\r\n"
                + "Host: example.com\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) decoded.get("request");
        assertEquals("GET", request.get("method"));
        assertEquals("/search", request.get("path"));
        assertEquals("q=test&page=2", request.get("query"));
        assertEquals("HTTP/1.1", request.get("version"));
    }

    @Test
    void leavesNonHttpPayloadAsRawText() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertFalse((Boolean) decoded.get("isHttp"));
        assertTrue(decoded.containsKey("rawText"));
    }

    @Test
    void decodesChunkedHttpBody() {
        byte[] payload = ("HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n"
                + "4\r\nWiki\r\n"
                + "5\r\npedia\r\n"
                + "0\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertTrue((Boolean) decoded.get("isHttp"));
        assertEquals("Wikipedia", decoded.get("bodyText"));
    }

    @Test
    void decodesGzipHttpBody() throws Exception {
        byte[] compressed = gzip("hello gzip");
        byte[] headers = ("HTTP/1.1 200 OK\r\n"
                + "Content-Encoding: gzip\r\n"
                + "Content-Length: " + compressed.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = new byte[headers.length + compressed.length];
        System.arraycopy(headers, 0, payload, 0, headers.length);
        System.arraycopy(compressed, 0, payload, headers.length, compressed.length);

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertTrue((Boolean) decoded.get("isHttp"));
        assertEquals("hello gzip", decoded.get("bodyText"));
    }

    @Test
    void decodesBrotliHttpBody() throws Exception {
        com.aayushatharva.brotli4j.Brotli4jLoader.ensureAvailability();
        byte[] compressed = compressBrotli("hello brotli");
        byte[] headers = ("HTTP/1.1 200 OK\r\n"
                + "Content-Encoding: br\r\n"
                + "Content-Length: " + compressed.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = new byte[headers.length + compressed.length];
        System.arraycopy(headers, 0, payload, 0, headers.length);
        System.arraycopy(compressed, 0, payload, headers.length, compressed.length);

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertTrue((Boolean) decoded.get("isHttp"));
        assertEquals("hello brotli", decoded.get("bodyText"));
    }

    private static byte[] gzip(String body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static byte[] compressBrotli(String body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var brotli = new com.aayushatharva.brotli4j.encoder.BrotliOutputStream(output)) {
            brotli.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
}
