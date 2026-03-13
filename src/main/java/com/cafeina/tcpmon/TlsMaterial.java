package com.cafeina.tcpmon;

import java.nio.file.Path;

public record TlsMaterial(
        Path certificateFile,
        Path privateKeyFile,
        Path keyStoreFile,
        String keyStorePassword,
        Path trustStoreFile,
        String trustStorePassword,
        String keyStoreType,
        String trustStoreType) {
}
