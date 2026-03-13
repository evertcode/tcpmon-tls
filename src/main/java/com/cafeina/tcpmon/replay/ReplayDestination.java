package com.cafeina.tcpmon.replay;

public enum ReplayDestination {
    LISTENER,
    TARGET;

    public static ReplayDestination fromString(String value) {
        if (value == null || value.isBlank()) {
            return LISTENER;
        }
        return ReplayDestination.valueOf(value.trim().toUpperCase());
    }
}
