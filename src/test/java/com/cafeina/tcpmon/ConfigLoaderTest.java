package com.cafeina.tcpmon;

import com.cafeina.tcpmon.config.ConfigFile;
import com.cafeina.tcpmon.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void writesExampleConfigFile() throws Exception {
        Path output = tempDir.resolve("tcpmon.json");
        TcpMonTlsCommand command = new TcpMonTlsCommand();

        command.writeExampleConfig(output);

        assertTrue(Files.exists(output));
        ConfigFile config = ConfigLoader.load(output);
        assertNotNull(config.ui());
        assertEquals(8080, config.ui().port());
        assertEquals("127.0.0.1", config.ui().host());
        assertTrue(Boolean.TRUE.equals(config.ui().enabled()));
        assertEquals("./sessions", config.sessionsDir());
    }

    @Test
    void writesExampleYamlConfigFile() throws Exception {
        Path output = tempDir.resolve("tcpmon.yaml");
        TcpMonTlsCommand command = new TcpMonTlsCommand();

        command.writeExampleConfig(output);

        assertTrue(Files.exists(output));
        ConfigFile config = ConfigLoader.load(output);
        assertNotNull(config.ui());
        assertEquals(8080, config.ui().port());
        assertEquals("127.0.0.1", config.ui().host());
        assertTrue(Boolean.TRUE.equals(config.ui().enabled()));
        assertEquals("./sessions", config.sessionsDir());
    }
}
