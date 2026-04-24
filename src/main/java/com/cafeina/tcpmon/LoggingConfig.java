package com.cafeina.tcpmon;

public record LoggingConfig(
        String level,
        String format,
        boolean accessLog,
        boolean metricsLog) {
    public static LoggingConfig defaults() {
        return new LoggingConfig("INFO", "text", false, false);
    }
}
