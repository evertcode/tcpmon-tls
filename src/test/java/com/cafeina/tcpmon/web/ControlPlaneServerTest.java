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
import java.util.List;
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

    @Test
    void summarizesCompletedKeepAliveExchangeAsNotLiveAndWithDuration(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.NONE,
                List.of("TLSv1.3"),
                List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            String sessionId = store.openSession("route-a", "client", "listener", "target");
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET, "GET /health HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".getBytes(), null, Map.of());

            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("summarizeSession", Map.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) method.invoke(server, store.listSessions().getFirst());

            assertEquals("GET", payload.get("requestMethod"));
            assertEquals("/health", payload.get("requestPath"));
            assertEquals("200", payload.get("responseStatusCode"));
            assertEquals(Boolean.FALSE, payload.get("live"));
            assertTrue(payload.get("durationMs") instanceof Long);
        }
    }

    @Test
    void summarizesKeepAliveConnectionAsMultipleRequestRows(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.NONE,
                List.of("TLSv1.3"),
                List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            String sessionId = store.openSession("route-a", "client", "listener", "target");
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET, (
                    "GET /one HTTP/1.1\r\nHost: example.com\r\n\r\n" +
                    "GET /two HTTP/1.1\r\nHost: example.com\r\n\r\n"
            ).getBytes(), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, (
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK" +
                    "HTTP/1.1 201 Created\r\nContent-Length: 3\r\n\r\nYES"
            ).getBytes(), null, Map.of());

            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("summarizeRequests");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) method.invoke(server);

            assertEquals(2, rows.size());
            assertEquals("/one", rows.get(0).get("requestPath"));
            assertEquals("200", rows.get(0).get("responseStatusCode"));
            assertEquals(0, rows.get(0).get("exchangeIndex"));
            assertEquals("/two", rows.get(1).get("requestPath"));
            assertEquals("201", rows.get(1).get("responseStatusCode"));
            assertEquals(1, rows.get(1).get("exchangeIndex"));
        }
    }

    @Test
    void requestSummariesDoNotSerializeLargePayloadBodies(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.NONE,
                List.of("TLSv1.3"),
                List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            String sessionId = store.openSession("route-a", "client", "listener", "target");
            String largeBody = "large-response-marker-" + "x".repeat(1024 * 1024);
            byte[] response = ("HTTP/1.1 200 OK\r\nContent-Length: " + largeBody.length() + "\r\n\r\n" + largeBody).getBytes();
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET, "GET /large HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT, response, null, Map.of());

            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("summarizeRequests");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) method.invoke(server);
            String json = JsonSupport.objectMapper().writeValueAsString(rows);

            assertEquals(1, rows.size());
            assertEquals("/large", rows.getFirst().get("requestPath"));
            assertEquals("200", rows.getFirst().get("responseStatusCode"));
            assertEquals((long) response.length, rows.getFirst().get("responseSizeBytes"));
            assertFalse(json.contains("large-response-marker"));
            assertFalse(json.contains("base64"));
            assertTrue(json.length() < 4096);
        }
    }

    @Test
    void loadsStaticWebAssetsFromClasspath() {
        String index = ControlPlaneServer.readWebText("/index.html");
        String favicon = ControlPlaneServer.readWebText("/favicon.svg");
        String stateJs = ControlPlaneServer.readWebText("/js/state.js");
        String js = ControlPlaneServer.readWebText("/js/app.js");
        String css = ControlPlaneServer.readWebText("/styles/app.css");
        String routesJs = ControlPlaneServer.readWebText("/js/routes.js");
        String sessionsJs = ControlPlaneServer.readWebText("/js/sessions.js");
        String detailsJs = ControlPlaneServer.readWebText("/js/details.js");
        String actionsJs = ControlPlaneServer.readWebText("/js/actions.js");

        assertTrue(index.contains("/assets/favicon.svg"));
        assertTrue(favicon.contains("<svg"));
        assertTrue(index.contains("/assets/styles/app.css"));
        assertTrue(index.contains("/assets/js/state.js"));
        assertTrue(index.contains("/assets/js/utils.js"));
        assertTrue(index.contains("/assets/js/api.js"));
        assertTrue(index.contains("/assets/js/routes.js"));
        assertTrue(index.contains("/assets/js/sessions.js"));
        assertTrue(index.contains("/assets/js/details.js"));
        assertTrue(index.contains("/assets/js/actions.js"));
        assertTrue(index.contains("/assets/js/app.js"));
        assertTrue(stateJs.contains("window.uiState"));
        assertTrue(stateJs.contains("function setState("));
        assertTrue(stateJs.contains("function patchState("));
        assertFalse(stateJs.contains("Object.defineProperty(window"));
        assertFalse(index.contains("onclick="));
        assertFalse(index.contains("oninput="));
        assertFalse(index.contains("onchange="));
        assertTrue(js.contains("initApp()"));
        assertTrue(js.contains("function renderApp("));
        assertTrue(js.contains("function renderListSection()"));
        assertTrue(js.contains("function renderSelectedSessionDetail()"));
        assertTrue(css.contains(".topbar"));
        assertTrue(routesJs.contains("function renderRouteList()"));
        assertTrue(routesJs.contains("function buildRouteListItem("));
        assertTrue(routesJs.contains("row.dataset.action = 'select-route'"));
        assertTrue(sessionsJs.contains("function renderRequestTable()"));
        assertTrue(sessionsJs.contains("function buildRequestTableElement("));
        assertTrue(sessionsJs.contains("row.dataset.action = 'select-session'"));
        assertTrue(detailsJs.contains("function renderPayloads("));
        assertTrue(detailsJs.contains("getState('lastLoadedSession')"));
        assertTrue(detailsJs.contains("function buildPayloadHeadersDetails("));
        assertTrue(detailsJs.contains("details.dataset.action = 'toggle-payload-headers'"));
        assertTrue(actionsJs.contains("async function exportHar()"));
        assertTrue(actionsJs.contains("getState('lastLoadedSession')"));
        assertTrue(actionsJs.contains("function buildPayloadActionButton("));
    }

    @Test
    void sessionDetailResponseStripsBase64FromExchangePayloads(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.NONE,
                List.of("TLSv1.3"),
                List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            String sessionId = store.openSession("route-a", "client", "listener", "target");
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET,
                    "GET /check HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT,
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".getBytes(), null, Map.of());

            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("enrichSession", Map.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> enriched = (Map<String, Object>) method.invoke(server, store.sessionDetails(sessionId));
            String json = JsonSupport.objectMapper().writeValueAsString(enriched);

            assertTrue(enriched.containsKey("sessionId"), "sessionId must be present for frontend replay");
            assertFalse(json.contains("\"base64\""), "base64 must not appear in enriched session response");
        }
    }

    @Test
    void sessionDetailTruncatesLargeBodyAndSetsTruncatedFlag(@TempDir Path tempDir) throws Exception {
        ProxyConfig config = new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, "token", null),
                tempDir,
                com.cafeina.tcpmon.InterceptMode.NONE,
                List.of("TLSv1.3"),
                List.of());
        RouteConfig route = new RouteConfig(
                "route-a",
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, null),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", false, true, false, null));

        try (SessionStore store = new SessionStore(tempDir.resolve("sessions"), JsonSupport.objectMapper())) {
            String sessionId = store.openSession("route-a", "client", "listener", "target");
            String largeBody = "B".repeat(PayloadInspector.BODY_PREVIEW_LIMIT + 1000);
            store.recordPayload(sessionId, Direction.CLIENT_TO_TARGET,
                    "GET /big HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(), null, Map.of());
            store.recordPayload(sessionId, Direction.TARGET_TO_CLIENT,
                    ("HTTP/1.1 200 OK\r\nContent-Length: " + largeBody.length() + "\r\n\r\n" + largeBody).getBytes(),
                    null, Map.of());

            ControlPlaneServer server = new ControlPlaneServer(
                    config,
                    new RouteRegistry(List.of(route), store),
                    (TcpMonProxy) null,
                    store,
                    new ReplayService(config, new RouteRegistry(List.of(route), null)));

            var method = ControlPlaneServer.class.getDeclaredMethod("enrichSession", Map.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> enriched = (Map<String, Object>) method.invoke(server, store.sessionDetails(sessionId));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exchanges = (List<Map<String, Object>>) enriched.get("exchanges");
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) exchanges.getFirst().get("response");
            @SuppressWarnings("unchecked")
            Map<String, Object> decoded = (Map<String, Object>) response.get("decoded");

            assertEquals(Boolean.TRUE, decoded.get("bodyTruncated"));
            String bodyText = (String) decoded.get("bodyText");
            assertEquals(PayloadInspector.BODY_PREVIEW_LIMIT, bodyText.length());
        }
    }

    @Test
    void resolvesAssetContentTypes() {
        assertEquals("text/css; charset=utf-8", ControlPlaneServer.assetContentType("app.css"));
        assertEquals("text/javascript; charset=utf-8", ControlPlaneServer.assetContentType("app.js"));
        assertEquals("text/html; charset=utf-8", ControlPlaneServer.assetContentType("index.html"));
        assertEquals("image/svg+xml", ControlPlaneServer.assetContentType("favicon.svg"));
        assertEquals("application/octet-stream", ControlPlaneServer.assetContentType("binary.bin"));
    }
}
