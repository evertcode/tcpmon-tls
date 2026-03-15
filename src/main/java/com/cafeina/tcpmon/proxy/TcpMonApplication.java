package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import com.cafeina.tcpmon.web.ControlPlaneServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public final class TcpMonApplication implements AutoCloseable {
    private final ProxyConfig config;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private TcpMonProxy proxy;
    private ControlPlaneServer controlPlaneServer;

    public TcpMonApplication(ProxyConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        SessionStore sessionStore = new SessionStore(config.sessionsDir(), JsonSupport.objectMapper());

        List<RouteConfig> initialRoutes;
        if (sessionStore.countRoutes() == 0) {
            for (RouteConfig r : config.routes()) sessionStore.insertRoute(r);
            initialRoutes = config.routes();
        } else {
            Map<String, RouteConfig> configById = config.routes().stream()
                    .collect(Collectors.toMap(RouteConfig::id, r -> r));
            initialRoutes = sessionStore.loadRoutes().stream()
                    .map(dbRoute -> {
                        RouteConfig configRoute = configById.get(dbRoute.id());
                        if (configRoute == null) return dbRoute;
                        ListenerConfig listener = new ListenerConfig(
                                dbRoute.listener().host(),
                                dbRoute.listener().port(),
                                dbRoute.listener().transportMode(),
                                dbRoute.listener().clientAuthMode(),
                                configRoute.listener().tlsMaterial());
                        TargetConfig target = new TargetConfig(
                                dbRoute.target().host(),
                                dbRoute.target().port(),
                                dbRoute.target().transportMode(),
                                dbRoute.target().sniHost(),
                                dbRoute.target().insecureTrustAll(),
                                dbRoute.target().verifyHostname(),
                                dbRoute.target().rewriteHostHeader(),
                                configRoute.target().tlsMaterial());
                        return new RouteConfig(dbRoute.id(), listener, target);
                    })
                    .toList();
        }

        RouteRegistry registry = new RouteRegistry(initialRoutes, sessionStore);
        this.proxy = new TcpMonProxy(config, registry, sessionStore);
        this.proxy.start();

        if (config.ui().enabled()) {
            this.controlPlaneServer = new ControlPlaneServer(
                    config,
                    registry,
                    proxy,
                    sessionStore,
                    new ReplayService(config, registry));
            this.controlPlaneServer.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        for (RouteConfig route : registry.routes()) {
            System.out.printf(
                    "route %s listening on %s:%d (%s) -> %s:%d (%s)%n",
                    route.id(),
                    route.listener().host(),
                    route.listener().port(),
                    route.listener().transportMode(),
                    route.target().host(),
                    route.target().port(),
                    route.target().transportMode());
        }
        if (config.ui().enabled()) {
            System.out.printf("control plane: http://%s:%d/%n", config.ui().host(), config.ui().port());
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    @Override
    public void close() {
        if (controlPlaneServer != null) {
            controlPlaneServer.close();
        }
        if (proxy != null) {
            proxy.close();
        }
        shutdownLatch.countDown();
    }
}
