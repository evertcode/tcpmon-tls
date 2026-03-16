package com.cafeina.tcpmon;

import java.nio.file.Path;
import java.util.List;

public record ProxyConfig(
        UiConfig ui,
        Path sessionsDir,
        InterceptMode interceptMode,
        List<String> enabledProtocols,
        List<String> enabledCiphers) {
}
