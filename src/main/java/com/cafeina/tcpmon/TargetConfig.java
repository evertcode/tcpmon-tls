package com.cafeina.tcpmon;

import java.util.List;

public record TargetConfig(
        String host,
        int port,
        TransportMode transportMode,
        String sniHost,
        boolean insecureTrustAll,
        boolean verifyHostname,
        boolean rewriteHostHeader,
        TlsMaterial tlsMaterial,
        List<String> enabledProtocols,
        List<String> enabledCiphers) {
}
