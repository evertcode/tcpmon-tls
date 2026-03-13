package com.cafeina.tcpmon;

public record UiConfig(
        String host,
        int port,
        boolean enabled) {
}
