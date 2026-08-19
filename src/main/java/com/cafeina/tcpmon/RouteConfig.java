package com.cafeina.tcpmon;

import java.util.List;

public record RouteConfig(
        String id,
        ListenerConfig listener,
        TargetConfig target,
        int requestDelayMs,
        int responseDelayMs,
        String interceptMethod,
        String interceptPathContains,
        List<MockRule> mockRules) {
}
