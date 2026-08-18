package com.cafeina.tcpmon;

import java.util.List;

public record ListenerConfig(
        String host,
        int port,
        TransportMode transportMode,
        ClientAuthMode clientAuthMode,
        TlsMaterial tlsMaterial,
        List<String> enabledProtocols,
        List<String> enabledCiphers,
        TlsMaterial replayIdentity) {
}
