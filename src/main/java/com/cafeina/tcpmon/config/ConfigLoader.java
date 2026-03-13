package com.cafeina.tcpmon.config;

import com.cafeina.tcpmon.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static ConfigFile load(Path path) throws IOException {
        return JsonSupport.objectMapper().readValue(Files.readString(path), ConfigFile.class);
    }
}
