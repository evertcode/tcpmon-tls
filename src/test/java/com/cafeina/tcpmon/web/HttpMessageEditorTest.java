package com.cafeina.tcpmon.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpMessageEditorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rebuildsHttpMessageAndFixesContentLength() throws Exception {
        byte[] payload = HttpMessageEditor.buildHttpMessage(objectMapper.readTree("""
                {
                  "startLine": "POST /items HTTP/1.1",
                  "headersText": "Host: example.com\\nContent-Length: 999\\nTransfer-Encoding: chunked",
                  "bodyText": "{\\"ok\\":true}"
                }
                """));

        String text = new String(payload, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("POST /items HTTP/1.1\r\n"));
        assertTrue(text.contains("Host: example.com\r\n"));
        assertTrue(text.contains("Content-Length: 11\r\n"));
        assertFalse(text.contains("Transfer-Encoding:"));
        assertTrue(text.endsWith("\r\n\r\n{\"ok\":true}"));
    }

    @Test
    void buildsRequestFromStructuredFields() throws Exception {
        byte[] payload = HttpMessageEditor.buildHttpMessage(objectMapper.readTree("""
                {
                  "method": "GET",
                  "path": "/search",
                  "query": "q=test&page=2",
                  "version": "HTTP/1.1",
                  "headersText": "Host: example.com",
                  "bodyText": ""
                }
                """));

        String text = new String(payload, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("GET /search?q=test&page=2 HTTP/1.1\r\n"));
        assertTrue(text.contains("Host: example.com\r\n"));
        assertFalse(text.contains("Content-Length:"));
    }
}
