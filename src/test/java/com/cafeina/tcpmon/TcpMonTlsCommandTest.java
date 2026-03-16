package com.cafeina.tcpmon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpMonTlsCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsApplyWhenNoFlagsGiven() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs();

        ProxyConfig config = command.toConfig();
        assertEquals("127.0.0.1", config.ui().host());
        assertEquals(8080, config.ui().port());
        assertTrue(config.ui().enabled());
        assertEquals(InterceptMode.NONE, config.interceptMode());
    }

    @Test
    void cliFlagsOverrideDefaults() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs(
                "--ui-host", "0.0.0.0",
                "--ui-port", "9090",
                "--ui-enabled=false",
                "--intercept-mode", "BOTH",
                "--sessions-dir", "/tmp/s");

        ProxyConfig config = command.toConfig();
        assertEquals("0.0.0.0", config.ui().host());
        assertEquals(9090, config.ui().port());
        assertFalse(config.ui().enabled());
        assertEquals(InterceptMode.BOTH, config.interceptMode());
        assertEquals(Path.of("/tmp/s"), config.sessionsDir());
    }

    @Test
    void loadsAppSettingsFromJsonFile() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "ui": {
                    "host": "127.0.0.1",
                    "port": 8081,
                    "enabled": true
                  },
                  "sessionsDir": "./sessions-json",
                  "interceptMode": "NONE",
                  "tlsProtocols": ["TLSv1.3", "TLSv1.2"]
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(8081, config.ui().port());
        assertEquals("127.0.0.1", config.ui().host());
        assertTrue(config.ui().enabled());
        assertEquals(2, config.enabledProtocols().size());
    }

    @Test
    void loadsAppSettingsFromYamlFile() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.yaml");
        Files.writeString(configPath, """
                ui:
                  host: 127.0.0.1
                  port: 8082
                  enabled: true
                sessionsDir: ./sessions-yaml
                interceptMode: NONE
                tlsProtocols:
                  - TLSv1.3
                  - TLSv1.2
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(8082, config.ui().port());
        assertEquals(2, config.enabledProtocols().size());
    }

    @Test
    void cliFlagsOverrideFileValues() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "ui": { "port": 8081, "enabled": true }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs(
                "--config", configPath.toString(),
                "--ui-port", "9999",
                "--ui-enabled=false");

        ProxyConfig config = command.toConfig();
        assertEquals(9999, config.ui().port());
        assertFalse(config.ui().enabled());
    }

    @Test
    void writesExampleConfig(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("example.json");
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        command.writeExampleConfig(out);

        assertTrue(Files.exists(out));
        String content = Files.readString(out);
        assertTrue(content.contains("ui"));
        assertTrue(content.contains("sessionsDir"));
    }
}
