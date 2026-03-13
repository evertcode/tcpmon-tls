package com.cafeina.tcpmon;

import java.nio.file.Path;
import java.util.List;

public record ProxyConfig(
        List<RouteConfig> routes,
        UiConfig ui,
        Path sessionsDir,
        InterceptMode interceptMode,
        List<String> enabledProtocols,
        List<String> enabledCiphers) {
    public ProxyConfig {
        routes = List.copyOf(routes);
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("At least one route is required");
        }
    }

    public ProxyConfig(
            ListenerConfig listener,
            TargetConfig target,
            UiConfig ui,
            Path sessionsDir,
            InterceptMode interceptMode,
            List<String> enabledProtocols,
            List<String> enabledCiphers) {
        this(
                List.of(new RouteConfig("default", listener, target)),
                ui,
                sessionsDir,
                interceptMode,
                enabledProtocols,
                enabledCiphers);
    }

    public RouteConfig primaryRoute() {
        return routes.getFirst();
    }

    public RouteConfig route(String routeId) {
        return routes.stream()
                .filter(route -> route.id().equals(routeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown route: " + routeId));
    }

    public ListenerConfig listener() {
        return primaryRoute().listener();
    }

    public TargetConfig target() {
        return primaryRoute().target();
    }
}
