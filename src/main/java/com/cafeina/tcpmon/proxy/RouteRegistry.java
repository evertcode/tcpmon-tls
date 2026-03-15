package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.session.SessionStore;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RouteRegistry {
    private final CopyOnWriteArrayList<RouteConfig> routes;
    private final SessionStore store;

    public RouteRegistry(List<RouteConfig> initial, SessionStore store) {
        this.routes = new CopyOnWriteArrayList<>(initial);
        this.store = store;
    }

    public List<RouteConfig> routes() {
        return Collections.unmodifiableList(routes);
    }

    public Optional<RouteConfig> findById(String id) {
        return routes.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public boolean add(RouteConfig route) {
        if (findById(route.id()).isPresent()) return false;
        store.insertRoute(route);
        routes.add(route);
        return true;
    }

    public boolean replace(String id, RouteConfig updated) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).id().equals(id)) {
                store.updateRoute(updated);
                routes.set(i, updated);
                return true;
            }
        }
        return false;
    }

    public boolean remove(String id) {
        if (findById(id).isEmpty()) return false;
        store.deleteRoute(id);
        return routes.removeIf(r -> r.id().equals(id));
    }
}
