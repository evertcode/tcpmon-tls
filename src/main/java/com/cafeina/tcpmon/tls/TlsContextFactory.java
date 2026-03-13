package com.cafeina.tcpmon.tls;

import com.cafeina.tcpmon.ClientAuthMode;
import com.cafeina.tcpmon.ProxyConfig;
import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.TargetConfig;
import com.cafeina.tcpmon.TlsMaterial;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;

public final class TlsContextFactory {
    private TlsContextFactory() {
    }

    public static SslContext buildServerContext(ProxyConfig config) throws GeneralSecurityException, IOException {
        return buildServerContext(config, config.listener());
    }

    public static SslContext buildServerContext(ProxyConfig config, RouteConfig route) throws GeneralSecurityException, IOException {
        return buildServerContext(config, route.listener());
    }

    public static SslContext buildServerContext(ProxyConfig config, com.cafeina.tcpmon.ListenerConfig listener) throws GeneralSecurityException, IOException {
        TlsMaterial material = listener.tlsMaterial();
        SslContextBuilder builder;
        if (material.certificateFile() != null && material.privateKeyFile() != null) {
            builder = SslContextBuilder.forServer(material.certificateFile().toFile(), material.privateKeyFile().toFile());
        } else if (material.keyStoreFile() != null) {
            builder = SslContextBuilder.forServer(loadKeyManagerFactory(
                    material.keyStoreFile(),
                    material.keyStorePassword(),
                    material.keyStoreType()));
        } else {
            throw new IllegalArgumentException("TLS listener requires --listen-cert/--listen-key or --listen-keystore");
        }

        applySharedSettings(builder, config.enabledProtocols(), config.enabledCiphers());
        if (material.trustStoreFile() != null) {
            if (isPem(material.trustStoreFile())) {
                builder.trustManager(material.trustStoreFile().toFile());
            } else {
                builder.trustManager(loadTrustManagerFactory(
                        material.trustStoreFile(),
                        material.trustStorePassword(),
                        material.trustStoreType()));
            }
        }
        builder.clientAuth(toNettyClientAuth(listener.clientAuthMode()));
        return builder.build();
    }

    public static SslContext buildClientContext(ProxyConfig config) throws GeneralSecurityException, IOException {
        return buildClientContext(config, config.target());
    }

    public static SslContext buildClientContext(ProxyConfig config, RouteConfig route) throws GeneralSecurityException, IOException {
        return buildClientContext(config, route.target());
    }

    public static SslContext buildClientContext(ProxyConfig config, TargetConfig target) throws GeneralSecurityException, IOException {
        TlsMaterial material = target.tlsMaterial();
        SslContextBuilder builder = SslContextBuilder.forClient();
        applySharedSettings(builder, config.enabledProtocols(), config.enabledCiphers());
        if (material.certificateFile() != null && material.privateKeyFile() != null) {
            builder.keyManager(material.certificateFile().toFile(), material.privateKeyFile().toFile());
        } else if (material.keyStoreFile() != null) {
            builder.keyManager(loadKeyManagerFactory(
                    material.keyStoreFile(),
                    material.keyStorePassword(),
                    material.keyStoreType()));
        }
        if (material.trustStoreFile() != null) {
            if (isPem(material.trustStoreFile())) {
                builder.trustManager(material.trustStoreFile().toFile());
            } else {
                builder.trustManager(loadTrustManagerFactory(
                        material.trustStoreFile(),
                    material.trustStorePassword(),
                    material.trustStoreType()));
            }
        } else if (target.insecureTrustAll()) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }
        return builder.build();
    }

    public static SslHandler newClientHandler(ProxyConfig config, SslContext context, ByteBufAllocator allocator) {
        return newClientHandler(config.target(), context, allocator);
    }

    public static SslHandler newClientHandler(RouteConfig route, SslContext context, ByteBufAllocator allocator) {
        return newClientHandler(route.target(), context, allocator);
    }

    public static SslHandler newClientHandler(TargetConfig target, SslContext context, ByteBufAllocator allocator) {
        String sniHost = target.sniHost() == null || target.sniHost().isBlank()
                ? target.host()
                : target.sniHost();
        SslHandler handler = context.newHandler(allocator, sniHost, target.port());
        if (target.verifyHostname()) {
            SSLParameters parameters = handler.engine().getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            handler.engine().setSSLParameters(parameters);
        }
        return handler;
    }

    private static void applySharedSettings(SslContextBuilder builder, List<String> protocols, List<String> ciphers) {
        if (!protocols.isEmpty()) {
            builder.protocols(protocols.toArray(String[]::new));
        }
        if (!ciphers.isEmpty()) {
            builder.ciphers(ciphers);
        }
    }

    private static ClientAuth toNettyClientAuth(ClientAuthMode mode) {
        return switch (mode) {
            case NONE -> ClientAuth.NONE;
            case OPTIONAL -> ClientAuth.OPTIONAL;
            case REQUIRE -> ClientAuth.REQUIRE;
        };
    }

    private static KeyManagerFactory loadKeyManagerFactory(Path keyStorePath, String password, String keyStoreType)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore(keyStorePath, password, keyStoreType);
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, password == null ? new char[0] : password.toCharArray());
        return factory;
    }

    private static TrustManagerFactory loadTrustManagerFactory(Path trustStorePath, String password, String trustStoreType)
            throws GeneralSecurityException, IOException {
        KeyStore trustStore = loadKeyStore(trustStorePath, password, trustStoreType);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        return factory;
    }

    private static KeyStore loadKeyStore(Path path, String password, String type) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(type == null || type.isBlank() ? inferKeyStoreType(path) : type);
        try (InputStream stream = Files.newInputStream(path)) {
            keyStore.load(stream, password == null ? new char[0] : password.toCharArray());
        }
        return keyStore;
    }

    private static String inferKeyStoreType(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".jks")) {
            return "JKS";
        }
        return "PKCS12";
    }

    private static boolean isPem(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".pem") || filename.endsWith(".crt") || filename.endsWith(".cer");
    }
}
