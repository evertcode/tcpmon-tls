package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import com.cafeina.tcpmon.web.ControlPlaneServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class TcpMonApplication implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TcpMonApplication.class);
    private final ProxyConfig config;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private TcpMonProxy proxy;
    private ControlPlaneServer controlPlaneServer;

    public TcpMonApplication(ProxyConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        log.info("Starting tcpmon-tls sessionsDir={} interceptMode={} uiEnabled={}",
                config.sessionsDir().toAbsolutePath(), config.interceptMode(), config.ui().enabled());
        SessionStore sessionStore = new SessionStore(config.sessionsDir(), JsonSupport.objectMapper());

        List<RouteConfig> initialRoutes = sessionStore.loadRoutes();
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
            log.info(
                    "Route {} listening on {}:{} ({}) -> {}:{} ({})",
                    route.id(),
                    route.listener().host(),
                    route.listener().port(),
                    route.listener().transportMode(),
                    route.target().host(),
                    route.target().port(),
                    route.target().transportMode());
        }
        if (config.ui().enabled()) {
            log.info("Control plane: {}://{}:{}/",
                    config.ui().tlsMaterial() == null ? "http" : "https",
                    config.ui().host(),
                    config.ui().port());
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    @Override
    public void close() {
        log.info("Stopping tcpmon-tls");
        if (controlPlaneServer != null) {
            controlPlaneServer.close();
        }
        if (proxy != null) {
            proxy.close();
        }
        shutdownLatch.countDown();
    }
}
