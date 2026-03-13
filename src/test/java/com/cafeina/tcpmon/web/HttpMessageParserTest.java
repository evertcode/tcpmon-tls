package com.cafeina.tcpmon.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpMessageParserTest {
    @Test
    void splitsMultipleHttpMessagesInSingleBuffer() {
        byte[] payload = ("GET /one HTTP/1.1\r\nHost: example.com\r\n\r\n"
                + "GET /two HTTP/1.1\r\nHost: example.com\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> messages = HttpMessageParser.splitMessages(payload);

        assertEquals(2, messages.size());
        assertEquals("GET /one HTTP/1.1\r\nHost: example.com\r\n\r\n", new String(messages.get(0), StandardCharsets.ISO_8859_1));
        assertEquals("GET /two HTTP/1.1\r\nHost: example.com\r\n\r\n", new String(messages.get(1), StandardCharsets.ISO_8859_1));
    }

    @Test
    void splitsChunkedHttpMessagesInSingleBuffer() {
        byte[] payload = ("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n"
                + "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello").getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> messages = HttpMessageParser.splitMessages(payload);

        assertEquals(2, messages.size());
    }
}
