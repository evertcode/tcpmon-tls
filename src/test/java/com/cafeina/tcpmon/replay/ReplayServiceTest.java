package com.cafeina.tcpmon.replay;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.InterceptMode;
import com.cafeina.tcpmon.ListenerConfig;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import com.cafeina.tcpmon.TransportMode;
import com.cafeina.tcpmon.UiConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayServiceTest {
    @Test
    void resolvesListenerEndpointForRecaptureMode() {
        ReplayService replayService = new ReplayService(new ProxyConfig(
                new ListenerConfig("127.0.0.1", 9000, TransportMode.PLAIN, ClientAuthMode.NONE, emptyTls()),
                new TargetConfig("example.com", 443, TransportMode.TLS, "example.com", true, false, false, emptyTls()),
                new UiConfig("127.0.0.1", 8080, true),
                Path.of("./sessions"),
                InterceptMode.NONE,
                List.of("TLSv1.3", "TLSv1.2"),
                List.of()));

        ReplayService.Endpoint endpoint = replayService.resolveEndpoint(ReplayDestination.LISTENER);

        assertEquals("127.0.0.1", endpoint.host());
        assertEquals(9000, endpoint.port());
    }

    private static TlsMaterial emptyTls() {
        return new TlsMaterial(null, null, null, null, null, null, "PKCS12", "PKCS12");
    }
}
