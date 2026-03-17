package com.cafeina.tcpmon.replay;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.InterceptMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.UiConfig;
import com.cafeina.tcpmon.proxy.RouteRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayServiceTest {
    @Test
    void resolvesListenerEndpointForRecaptureMode() {
        ListenerConfig listener = new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, emptyTls());
        TargetConfig target = new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", true, false, false, emptyTls());
        RouteConfig route = new RouteConfig("default", listener, target);
        ProxyConfig config = replayServiceConfig();
        ReplayService replayService = new ReplayService(config, new RouteRegistry(List.of(route), null));

        ReplayService.Endpoint endpoint = replayService.resolveEndpoint(route, ReplayDestination.LISTENER);

        assertEquals("127.0.0.1", endpoint.host());
        assertEquals(9000, endpoint.port());
    }

    @Test
    void replayWaitsForResponseBytes() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread server = Thread.ofVirtual().start(() -> {
                try (Socket socket = serverSocket.accept();
                     InputStream input = socket.getInputStream();
                     OutputStream output = socket.getOutputStream()) {
                    input.readNBytes(18);
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                    output.flush();
                    Thread.sleep(200);
                } catch (Exception ignored) {
                }
            });

            ListenerConfig listener = new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, emptyTls());
            TargetConfig target = new TargetConfig("127.0.0.1", serverSocket.getLocalPort(), TransportMode.PLAIN, null, false, false, false, emptyTls());
            RouteConfig route = new RouteConfig("default", listener, target);
            ProxyConfig cfg = replayServiceConfig();
            ReplayService replayService = new ReplayService(cfg, new RouteRegistry(List.of(route), null));

            Map<String, Object> result = replayService.replay(
                    "GET / HTTP/1.1\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                    "default",
                    ReplayDestination.TARGET);

            assertTrue(((Integer) result.get("bytesReceived")) > 0);
            server.join(1_000);
        }
    }

    private static TlsMaterial emptyTls() {
        return new TlsMaterial(null, null, null, null, null, null, "PKCS12", "PKCS12");
    }

    private static ProxyConfig replayServiceConfig() {
        return new ProxyConfig(
                new UiConfig("127.0.0.1", 8080, true, null, null),
                Path.of("./sessions"),
                InterceptMode.NONE,
                List.of("TLSv1.3", "TLSv1.2"),
                List.of());
    }
}
