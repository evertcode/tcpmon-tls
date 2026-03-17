package com.cafeina.tcpmon.config;

import java.util.List;

public record ConfigFile(
        UiSection ui,
        String sessionsDir,
        String interceptMode,
        List<String> tlsProtocols,
        List<String> tlsCiphers) {
    public record UiSection(
            String host,
            Integer port,
            Boolean enabled,
            String apiToken,
            String tlsKeystore,
            String tlsKeystorePassword,
            String tlsKeystoreType) {
    }
}
