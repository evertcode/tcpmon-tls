package com.cafeina.tcpmon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpMonTlsCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsBooleanFlagsWithoutExplicitValue() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        CommandLine commandLine = new CommandLine(command);

        commandLine.parseArgs(
                "--listen-port", "9000",
                "--target-host", "example.com",
                "--target-port", "443",
                "--target-insecure",
                "--rewrite-host-header");

        ProxyConfig config = command.toConfig();
        assertTrue(config.target().insecureTrustAll());
        assertTrue(config.target().rewriteHostHeader());
        assertTrue(config.ui().enabled());
    }

    @Test
    void acceptsBooleanFlagsWithExplicitValue() {
        TcpMonTlsCommand command = new TcpMonTlsCommand();
        CommandLine commandLine = new CommandLine(command);

        commandLine.parseArgs(
                "--listen-port", "9000",
                "--target-host", "example.com",
                "--target-port", "443",
                "--target-insecure=true",
                "--rewrite-host-header=true",
                "--ui-enabled=false",
                "--target-verify-hostname=true");

        ProxyConfig config = command.toConfig();
        assertTrue(config.target().insecureTrustAll());
        assertTrue(config.target().rewriteHostHeader());
        assertTrue(config.target().verifyHostname());
        assertFalse(config.ui().enabled());
    }

    @Test
    void loadsConfigurationFromJsonFile() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "listener": {
                    "host": "127.0.0.1",
                    "port": 9100,
                    "mode": "PLAIN"
                  },
                  "target": {
                    "host": "jsonplaceholder.typicode.com",
                    "port": 443,
                    "mode": "TLS",
                    "sni": "jsonplaceholder.typicode.com",
                    "insecure": true,
                    "rewriteHostHeader": true
                  },
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
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(9100, config.listener().port());
        assertEquals("jsonplaceholder.typicode.com", config.target().host());
        assertTrue(config.target().insecureTrustAll());
        assertTrue(config.target().rewriteHostHeader());
        assertEquals(8081, config.ui().port());
    }

    @Test
    void loadsConfigurationFromYamlFile() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.yaml");
        Files.writeString(configPath, """
                listener:
                  host: 127.0.0.1
                  port: 9100
                  mode: PLAIN
                target:
                  host: jsonplaceholder.typicode.com
                  port: 443
                  mode: TLS
                  sni: jsonplaceholder.typicode.com
                  insecure: true
                  rewriteHostHeader: true
                ui:
                  host: 127.0.0.1
                  port: 8081
                  enabled: true
                sessionsDir: ./sessions-yaml
                interceptMode: NONE
                tlsProtocols:
                  - TLSv1.3
                  - TLSv1.2
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(9100, config.listener().port());
        assertEquals("jsonplaceholder.typicode.com", config.target().host());
        assertTrue(config.target().insecureTrustAll());
        assertTrue(config.target().rewriteHostHeader());
        assertEquals(8081, config.ui().port());
    }

    @Test
    void cliOverridesConfigFileValues() throws Exception {
        Path configPath = tempDir.resolve("tcpmon.json");
        Files.writeString(configPath, """
                {
                  "listener": { "port": 9100 },
                  "target": {
                    "host": "jsonplaceholder.typicode.com",
                    "port": 443,
                    "mode": "TLS",
                    "rewriteHostHeader": false
                  },
                  "ui": { "enabled": false }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(
                "--config", configPath.toString(),
                "--listen-port", "9200",
                "--rewrite-host-header=true",
                "--ui-enabled=true");

        ProxyConfig config = command.toConfig();
        assertEquals(9200, config.listener().port());
        assertTrue(config.target().rewriteHostHeader());
        assertTrue(config.ui().enabled());
    }

    @Test
    void loadsMultipleRoutesFromJsonFile() throws Exception {
        Path configPath = tempDir.resolve("tcpmon-routes.json");
        Files.writeString(configPath, """
                {
                  "routes": [
                    {
                      "id": "public-http",
                      "listener": {
                        "host": "127.0.0.1",
                        "port": 9000,
                        "mode": "PLAIN"
                      },
                      "target": {
                        "host": "jsonplaceholder.typicode.com",
                        "port": 443,
                        "mode": "TLS",
                        "sni": "jsonplaceholder.typicode.com",
                        "insecure": true,
                        "rewriteHostHeader": true
                      }
                    },
                    {
                      "id": "internal-http",
                      "listener": {
                        "host": "127.0.0.1",
                        "port": 9001,
                        "mode": "PLAIN"
                      },
                      "target": {
                        "host": "example.org",
                        "port": 80,
                        "mode": "PLAIN"
                      }
                    }
                  ],
                  "ui": {
                    "host": "127.0.0.1",
                    "port": 8081,
                    "enabled": true
                  }
                }
                """);

        TcpMonTlsCommand command = new TcpMonTlsCommand();
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs("--config", configPath.toString());

        ProxyConfig config = command.toConfig();
        assertEquals(2, config.routes().size());
        assertEquals("public-http", config.routes().get(0).id());
        assertEquals(9000, config.routes().get(0).listener().port());
        assertEquals("jsonplaceholder.typicode.com", config.routes().get(0).target().host());
        assertTrue(config.routes().get(0).target().rewriteHostHeader());
        assertEquals("internal-http", config.routes().get(1).id());
        assertEquals(9001, config.routes().get(1).listener().port());
        assertEquals("example.org", config.routes().get(1).target().host());
        assertEquals(8081, config.ui().port());
    }
}
