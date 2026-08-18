package com.cafeina.tcpmon;

public record RouteConfig(
        String id,
        ListenerConfig listener,
        TargetConfig target,
        int requestDelayMs,
        int responseDelayMs,
        String interceptMethod,
        String interceptPathContains) {
}
