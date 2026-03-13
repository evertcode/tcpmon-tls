package com.cafeina.tcpmon;

public record TargetConfig(
        String host,
        int port,
        TransportMode transportMode,
        String sniHost,
        boolean insecureTrustAll,
        boolean verifyHostname,
        boolean rewriteHostHeader,
        TlsMaterial tlsMaterial) {
}
