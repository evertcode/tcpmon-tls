package com.cafeina.tcpmon;

import com.cafeina.tcpmon.config.ConfigFile;
import com.cafeina.tcpmon.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(9000, config.listener().port());
        assertEquals("jsonplaceholder.typicode.com", config.target().host());
        assertTrue(Boolean.TRUE.equals(config.target().insecure()));
        assertTrue(Boolean.TRUE.equals(config.target().rewriteHostHeader()));
    }
}
