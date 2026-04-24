package com.cafeina.tcpmon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals("INFO", config.logging().level());
        assertEquals("text", config.logging().format());
        assertFalse(config.logging().accessLog());
        assertFalse(config.logging().metricsLog());
    }

    @Test
    void cliFlagsOverrideDefaults() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs(
                "--ui-host", "0.0.0.0",
                "--ui-port", "9090",
                "--ui-enabled=false",
                "--intercept-mode", "BOTH",
                "--log-level", "DEBUG",
                "--log-format", "json",
                "--access-log",
                "--metrics-log",
                "--sessions-dir", "/tmp/s");

        ProxyConfig config = command.toConfig();
        assertEquals("0.0.0.0", config.ui().host());
        assertEquals(9090, config.ui().port());
        assertFalse(config.ui().enabled());
        assertEquals(InterceptMode.BOTH, config.interceptMode());
        assertEquals(Path.of("/tmp/s"), config.sessionsDir());
        assertEquals("DEBUG", config.logging().level());
        assertEquals("json", config.logging().format());
        assertTrue(config.logging().accessLog());
        assertTrue(config.logging().metricsLog());
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
                  "tlsProtocols": ["TLSv1.3", "TLSv1.2"],
                  "logging": {
                    "level": "WARN",
                    "format": "json",
                    "accessLog": true,
                    "metricsLog": true
                  }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(8081, config.ui().port());
        assertEquals("127.0.0.1", config.ui().host());
        assertTrue(config.ui().enabled());
        assertEquals(2, config.enabledProtocols().size());
        assertEquals("WARN", config.logging().level());
        assertEquals("json", config.logging().format());
        assertTrue(config.logging().accessLog());
        assertTrue(config.logging().metricsLog());
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
                logging:
                  level: DEBUG
                  format: text
                  accessLog: true
                  metricsLog: false
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(8082, config.ui().port());
        assertEquals(2, config.enabledProtocols().size());
        assertEquals("DEBUG", config.logging().level());
        assertEquals("text", config.logging().format());
        assertTrue(config.logging().accessLog());
        assertFalse(config.logging().metricsLog());
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

    @Test
    void apiTokenIsNullByDefault() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs();

        ProxyConfig config = command.toConfig();
        assertNull(config.ui().apiToken());
        assertNull(config.ui().tlsMaterial());
    }

    @Test
    void uiTokenCliFlagIsWiredToApiToken() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--ui-token", "secret-token");

        ProxyConfig config = command.toConfig();
        assertEquals("secret-token", config.ui().apiToken());
    }

    @Test
    void uiTokenFromConfigFileIsLoaded() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "ui": {
                    "host": "127.0.0.1",
                    "port": 8080,
                    "apiToken": "file-token"
                  }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals("file-token", config.ui().apiToken());
    }

    @Test
    void cliTokenOverridesFileToken() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "ui": { "apiToken": "file-token" }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs("--config", configPath.toString(), "--ui-token", "cli-token");

        ProxyConfig config = command.toConfig();
        assertEquals("cli-token", config.ui().apiToken());
    }

    @Test
    void uiTlsKeystoreCliFlagBuildsUiTlsMaterial(@TempDir Path dir) {
        Path fakeKeystore = dir.resolve("ui.p12");
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        new CommandLine(command).parseArgs(
                "--ui-tls-keystore", fakeKeystore.toString(),
                "--ui-tls-keystore-password", "changeit",
                "--ui-tls-keystore-type", "PKCS12");

        ProxyConfig config = command.toConfig();
        assertNotNull(config.ui().tlsMaterial());
        assertEquals(fakeKeystore, config.ui().tlsMaterial().keyStoreFile());
        assertEquals("changeit", config.ui().tlsMaterial().keyStorePassword());
        assertEquals("PKCS12", config.ui().tlsMaterial().keyStoreType());
    }
}
