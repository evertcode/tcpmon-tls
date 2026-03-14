package com.cafeina.tcpmon.config;

import com.cafeina.tcpmon.util.JsonSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static ConfigFile load(Path path) throws IOException {
        return mapperFor(path).readValue(Files.readString(path), ConfigFile.class);
    }

    public static String write(ConfigFile config, Path path) throws IOException {
        ObjectMapper mapper = mapperFor(path);
        if (isYaml(path)) {
            return mapper.writeValueAsString(config);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
    }

    private static ObjectMapper mapperFor(Path path) {
        return isYaml(path) ? yamlMapper() : JsonSupport.objectMapper();
    }

    private static boolean isYaml(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private static ObjectMapper yamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
