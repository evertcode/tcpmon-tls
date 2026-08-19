package com.cafeina.tcpmon;

public record MockRule(
        String method,
        String pathContains,
        int statusCode,
        String headers,
        String body) {
}
