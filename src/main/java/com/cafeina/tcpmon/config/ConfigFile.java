package com.cafeina.tcpmon.config;

import java.util.List;

public record ConfigFile(
        ListenerSection listener,
        TargetSection target,
        UiSection ui,
        String sessionsDir,
        String interceptMode,
        List<String> tlsProtocols,
        List<String> tlsCiphers) {
    public record ListenerSection(
            String host,
            Integer port,
            String mode,
            String clientAuth,
            TlsSection tls) {
    }

    public record TargetSection(
            String host,
            Integer port,
            String mode,
            String sni,
            Boolean insecure,
            Boolean verifyHostname,
            Boolean rewriteHostHeader,
            TlsSection tls) {
    }

    public record UiSection(
            String host,
            Integer port,
            Boolean enabled) {
    }

    public record TlsSection(
            String cert,
            String key,
            String keyStore,
            String keyStorePassword,
            String trustStore,
            String trustStorePassword,
            String keyStoreType,
            String trustStoreType) {
    }
}
