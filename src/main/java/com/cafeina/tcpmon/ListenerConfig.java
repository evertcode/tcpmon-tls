package com.cafeina.tcpmon;

public record ListenerConfig(
        String host,
        int port,
        TransportMode transportMode,
        ClientAuthMode clientAuthMode,
        TlsMaterial tlsMaterial) {
}
