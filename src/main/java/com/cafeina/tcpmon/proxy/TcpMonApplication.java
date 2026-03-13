package com.cafeina.tcpmon.proxy;

import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.util.JsonSupport;
import com.cafeina.tcpmon.web.ControlPlaneServer;

import java.util.concurrent.CountDownLatch;

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
        this.proxy = new TcpMonProxy(config, sessionStore);
        this.proxy.start();
        if (config.ui().enabled()) {
            this.controlPlaneServer = new ControlPlaneServer(
                    config.ui(),
                    sessionStore,
                    new ReplayService(config));
            this.controlPlaneServer.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        for (RouteConfig route : config.routes()) {
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
