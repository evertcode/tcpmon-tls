package com.cafeina.tcpmon.proxy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestRewriterTest {
    @Test
    void rewritesExistingHostHeader() {
        byte[] payload = ("GET /posts/1 HTTP/1.1\r\n"
                + "Host: 127.0.0.1:9000\r\n"
                + "User-Agent: curl\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

        HttpRequestRewriter.RewriteResult result = HttpRequestRewriter.rewriteHostHeader(
                payload,
                "jsonplaceholder.typicode.com",
                443);

        String rewritten = new String(result.payload(), StandardCharsets.ISO_8859_1);
        assertTrue(result.rewritten());
        assertTrue(rewritten.contains("Host: jsonplaceholder.typicode.com\r\n"));
        assertFalse(rewritten.contains("Host: 127.0.0.1:9000\r\n"));
    }

    @Test
    void leavesNonHttpPayloadUntouched() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};

        HttpRequestRewriter.RewriteResult result = HttpRequestRewriter.rewriteHostHeader(payload, "example.com", 443);

        assertFalse(result.rewritten());
        assertTrue(java.util.Arrays.equals(payload, result.payload()));
    }
}
