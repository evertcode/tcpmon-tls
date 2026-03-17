package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.Direction;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.UiConfig;
import com.cafeina.tcpmon.session.SessionEvent;
import com.cafeina.tcpmon.session.SessionStore;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.proxy.RouteRegistry;
import com.cafeina.tcpmon.proxy.TcpMonProxy;
import com.cafeina.tcpmon.replay.ReplayService;
import com.cafeina.tcpmon.util.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneServerTest {
    @Test
    void replayIsAllowedOnlyForClientToTargetPayloads() {
        SessionEvent requestPayload = new SessionEvent(
                "e-1", "PAYLOAD", Instant.now(), Direction.CLIENT_TO_TARGET, 10, null, null, null, null, Map.of("base64", "AQ=="));
        SessionEvent responsePayload = new SessionEvent(
                "e-2", "PAYLOAD", Instant.now(), Direction.TARGET_TO_CLIENT, 10, null, null, null, null, Map.of("base64", "AQ=="));
        SessionEvent lifecycle = new SessionEvent(
                "e-3", "TARGET_CONNECTED", Instant.now(), null, 0, null, null, null, null, Map.of());

        assertTrue(ControlPlaneServer.isReplayable(requestPayload));
        assertFalse(ControlPlaneServer.isReplayable(responsePayload));
        assertFalse(ControlPlaneServer.isReplayable(lifecycle));
    }

    @Test
    void readsNamedCookieValue() {
        assertEquals("secret", ControlPlaneServer.readCookie("a=1; tcpmon_auth=secret; b=2", "tcpmon_auth"));
        assertNull(ControlPlaneServer.readCookie("a=1; b=2", "tcpmon_auth"));
    }

    @Test
    void buildsSecureAuthCookieHeader() {
        String cookieHeader = ControlPlaneServer.buildAuthCookieHeader("secret-token", true);

        assertTrue(cookieHeader.contains("tcpmon_auth=secret-token"));
        assertTrue(cookieHeader.contains("HttpOnly"));
        assertTrue(cookieHeader.contains("SameSite=Strict"));
        assertTrue(cookieHeader.contains("Secure"));
    }

    @Test
    void sanitizesExceptionMessages() {
        assertEquals("boom", ControlPlaneServer.sanitizeExceptionMessage(new IllegalStateException("boom")));
        assertEquals("IllegalStateException", ControlPlaneServer.sanitizeExceptionMessage(new IllegalStateException()));
    }

    @Test
    void mergesTlsSecretsUsingPreserveSentinel() {
        TlsMaterial original = new TlsMaterial(
                Path.of("/tmp/listener.crt"),
                Path.of("/tmp/listener.key"),
                Path.of("/tmp/listener.p12"),
                "stored-key-password",
                Path.of("/tmp/trust.p12"),
                "stored-trust-password",
                "PKCS12",
                "PKCS12");
        TlsMaterial updated = new TlsMaterial(
                Path.of("/tmp/listener.crt"),
                Path.of("/tmp/listener.key"),
                Path.of("/tmp/listener.p12"),
                ControlPlaneServer.PRESERVE_SECRET,
                Path.of("/tmp/trust.p12"),
                ControlPlaneServer.PRESERVE_SECRET,
                "PKCS12",
                "PKCS12");

        TlsMaterial merged = ControlPlaneServer.mergeTlsMaterial(original, updated);

        assertEquals("stored-key-password", merged.keyStorePassword());
        assertEquals("stored-trust-password", merged.trustStorePassword());
    }

    @Test
    void mergesRouteTlsSecretsOnUpdate() {
        RouteConfig original = new RouteConfig(
                "route-a",
                new ListenerConfig(
                        "127.0.0.1",
                        9000,
                        TransportMode.TLS,
                        ClientAuthMode.NONE,
                        new TlsMaterial(null, null, Path.of("/tmp/listener.p12"), "listener-secret", null, null, "PKCS12", "PKCS12")),
                new TargetConfig(
                        "backend",
                        443,
                        TransportMode.TLS,
                        null,
                        false,
                        true,
                        false,
                        new TlsMaterial(null, null, Path.of("/tmp/target.p12"), "target-secret", null, null, "PKCS12", "PKCS12")));
        RouteConfig updated = new RouteConfig(
                "route-a",
                new ListenerConfig(
                        "127.0.0.1",
                        9000,
                        TransportMode.TLS,
                        ClientAuthMode.NONE,
                        new TlsMaterial(null, null, Path.of("/tmp/listener.p12"), ControlPlaneServer.PRESERVE_SECRET, null, null, "PKCS12", "PKCS12")),
                new TargetConfig(
                        "backend",
                        443,
                        TransportMode.TLS,
                        null,
                        false,
                        true,
                        false,
                        new TlsMaterial(null, null, Path.of("/tmp/target.p12"), "new-target-secret", null, null, "PKCS12", "PKCS12")));

        RouteConfig merged = ControlPlaneServer.mergeTlsSecrets(original, updated);

        assertEquals("listener-secret", merged.listener().tlsMaterial().keyStorePassword());
        assertEquals("new-target-secret", merged.target().tlsMaterial().keyStorePassword());
    }

    @Test
    void buildsRuntimePayload(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.REQUEST,
                java.util.List.of("TLSv1.3"),
                java.util.List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            store.openSession("route-a", "client", "listener", "target");
            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(java.util.List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(java.util.List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("buildRuntimePayload");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) method.invoke(server);

            assertEquals("UP", payload.get("status"));
            assertEquals(1, payload.get("routeCount"));
            assertEquals(1, payload.get("sessionCount"));
            assertEquals(0, payload.get("liveUpdateClients"));
        }
    }
}
