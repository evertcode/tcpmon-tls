package com.cafeina.tcpmon.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
        List<Map<String, String>> headers = (List<Map<String, String>>) decoded.get("headers");
        assertEquals("Host", headers.getFirst().get("name"));
        assertEquals("example.com", headers.getFirst().get("value"));
    }

    @Test
    void leavesNonHttpPayloadAsRawText() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};

        Map<String, Object> decoded = PayloadInspector.inspectBytes(payload);

        assertFalse((Boolean) decoded.get("isHttp"));
        assertTrue(decoded.containsKey("rawText"));
    }
}
