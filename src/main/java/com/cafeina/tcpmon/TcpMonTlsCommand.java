package com.cafeina.tcpmon;

import com.cafeina.tcpmon.config.ConfigFile;
import com.cafeina.tcpmon.config.ConfigLoader;
import com.cafeina.tcpmon.proxy.TcpMonApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "tcpmon-tls", mixinStandardHelpOptions = true, description = "TCP/TLS monitor with interception, replay and local web UI")
public final class TcpMonTlsCommand implements Callable<Integer> {
    @Option(names = "--init-config", description = "Write an example JSON config file to this path and exit.")
    private Path initConfigFile;

    @Option(names = "--config", description = "Path to a JSON or YAML config file. CLI flags override file values.")
    private Path configFile;

    @Option(names = "--listen-host")
    private String listenHost;

    @Option(names = "--listen-port")
    private Integer listenPort;

    @Option(names = "--listen-mode")
    private TransportMode listenMode;

    @Option(names = "--listen-client-auth")
    private ClientAuthMode listenClientAuth;

    @Option(names = "--listen-cert")
    private Path listenCert;

    @Option(names = "--listen-key")
    private Path listenKey;

    @Option(names = "--listen-keystore")
    private Path listenKeyStore;

    @Option(names = "--listen-keystore-password")
    private String listenKeyStorePassword;

    @Option(names = "--listen-keystore-type")
    private String listenKeyStoreType;

    @Option(names = "--listen-truststore")
    private Path listenTrustStore;

    @Option(names = "--listen-truststore-password")
    private String listenTrustStorePassword;

    @Option(names = "--listen-truststore-type")
    private String listenTrustStoreType;

    @Option(names = "--target-host")
    private String targetHost;

    @Option(names = "--target-port")
    private Integer targetPort;

    @Option(names = "--target-mode")
    private TransportMode targetMode;

    @Option(names = "--target-sni")
    private String targetSni;

    @Option(names = "--target-insecure", fallbackValue = "true", arity = "0..1", description = "Disable remote certificate validation for outbound TLS. Intended for local testing only.")
    private Boolean targetInsecure;

    @Option(names = "--target-verify-hostname", fallbackValue = "true", arity = "0..1")
    private Boolean targetVerifyHostname;

    @Option(names = "--rewrite-host-header", fallbackValue = "true", arity = "0..1", description = "Rewrite HTTP Host header to match the configured remote target.")
    private Boolean rewriteHostHeader;

    @Option(names = "--target-cert")
    private Path targetCert;

    @Option(names = "--target-key")
    private Path targetKey;

    @Option(names = "--target-keystore")
    private Path targetKeyStore;

    @Option(names = "--target-keystore-password")
    private String targetKeyStorePassword;

    @Option(names = "--target-keystore-type")
    private String targetKeyStoreType;

    @Option(names = "--target-truststore")
    private Path targetTrustStore;

    @Option(names = "--target-truststore-password")
    private String targetTrustStorePassword;

    @Option(names = "--target-truststore-type")
    private String targetTrustStoreType;

    @Option(names = "--sessions-dir")
    private Path sessionsDir;

    @Option(names = "--ui-enabled", fallbackValue = "true", arity = "0..1")
    private Boolean uiEnabled;

    @Option(names = "--ui-host")
    private String uiHost;

    @Option(names = "--ui-port")
    private Integer uiPort;

    @Option(names = "--intercept-mode")
    private InterceptMode interceptMode;

    @Option(names = "--tls-protocol", split = ",")
    private String[] tlsProtocols;

    @Option(names = "--tls-cipher", split = ",")
    private String[] tlsCiphers = new String[0];

    @Override
    public Integer call() throws Exception {
        if (initConfigFile != null) {
            writeExampleConfig(initConfigFile);
            System.out.println("Example config written to " + initConfigFile.toAbsolutePath());
            return 0;
        }

        ProxyConfig config = toConfig();

        try (TcpMonApplication app = new TcpMonApplication(config)) {
            app.start();
            app.blockUntilShutdown();
        }

        return 0;
    }

    void writeExampleConfig(Path outputPath) throws Exception {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                outputPath,
                ConfigLoader.write(exampleConfigFile(), outputPath) + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    ProxyConfig toConfig() {
        ConfigFile fileConfig = loadConfigFile();
        String effectiveUiHost = select(uiHost,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().host(), "127.0.0.1");
        Integer effectiveUiPort = select(uiPort,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().port(), 8080);
        boolean effectiveUiEnabled = selectBoolean(uiEnabled,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().enabled(), true);
        Path effectiveSessionsDir = pathValue(sessionsDir, fileConfig == null ? null : fileConfig.sessionsDir(),
                Path.of("./sessions"));
        InterceptMode effectiveInterceptMode = enumValue(interceptMode,
                fileConfig == null ? null : fileConfig.interceptMode(), InterceptMode.class, InterceptMode.NONE);
        List<String> effectiveProtocols = (tlsProtocols != null && tlsProtocols.length > 0)
                ? Arrays.asList(tlsProtocols)
                : fileConfig != null && fileConfig.tlsProtocols() != null && !fileConfig.tlsProtocols().isEmpty()
                        ? fileConfig.tlsProtocols()
                        : List.of("TLSv1.3", "TLSv1.2");
        List<String> effectiveCiphers = (tlsCiphers != null && tlsCiphers.length > 0)
                ? List.of(tlsCiphers)
                : fileConfig != null && fileConfig.tlsCiphers() != null
                        ? fileConfig.tlsCiphers()
                        : List.of();

        List<RouteConfig> effectiveRoutes = buildRoutes(fileConfig);

        return new ProxyConfig(
                effectiveRoutes,
                new UiConfig(effectiveUiHost, effectiveUiPort, effectiveUiEnabled),
                effectiveSessionsDir,
                effectiveInterceptMode,
                effectiveProtocols,
                effectiveCiphers);
    }

    private List<RouteConfig> buildRoutes(ConfigFile fileConfig) {
        if (fileConfig != null && fileConfig.routes() != null && !fileConfig.routes().isEmpty()) {
            return fileConfig.routes().stream()
                    .map(this::routeFromFile)
                    .toList();
        }

        String effectiveListenHost = select(listenHost,
                fileConfig == null ? null : fileConfig.listener() == null ? null : fileConfig.listener().host(),
                "0.0.0.0");
        Integer effectiveListenPort = select(listenPort,
                fileConfig == null ? null : fileConfig.listener() == null ? null : fileConfig.listener().port(), null);
        TransportMode effectiveListenMode = enumValue(listenMode,
                fileConfig == null ? null : fileConfig.listener() == null ? null : fileConfig.listener().mode(),
                TransportMode.class, TransportMode.PLAIN);
        ClientAuthMode effectiveClientAuth = enumValue(listenClientAuth,
                fileConfig == null ? null : fileConfig.listener() == null ? null : fileConfig.listener().clientAuth(),
                ClientAuthMode.class, ClientAuthMode.NONE);
        String effectiveTargetHost = select(targetHost,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().host(), null);
        Integer effectiveTargetPort = select(targetPort,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().port(), null);
        TransportMode effectiveTargetMode = enumValue(targetMode,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().mode(),
                TransportMode.class, TransportMode.PLAIN);
        String effectiveTargetSni = select(targetSni,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().sni(), null);
        boolean effectiveTargetInsecure = selectBoolean(targetInsecure,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().insecure(), false);
        boolean effectiveTargetVerifyHostname = selectBoolean(targetVerifyHostname,
                fileConfig == null ? null : fileConfig.target() == null ? null : fileConfig.target().verifyHostname(),
                false);
        boolean effectiveRewriteHostHeader = selectBoolean(rewriteHostHeader, fileConfig == null ? null
                : fileConfig.target() == null ? null : fileConfig.target().rewriteHostHeader(), false);
        validateRequired(effectiveListenPort, effectiveTargetHost, effectiveTargetPort);

        return List.of(new RouteConfig(
                "default",
                new ListenerConfig(
                        effectiveListenHost,
                        effectiveListenPort,
                        effectiveListenMode,
                        effectiveClientAuth,
                        mergeTls(
                                listenCert,
                                listenKey,
                                listenKeyStore,
                                listenKeyStorePassword,
                                listenTrustStore,
                                listenTrustStorePassword,
                                listenKeyStoreType,
                                listenTrustStoreType,
                                fileConfig == null || fileConfig.listener() == null ? null
                                        : fileConfig.listener().tls())),
                new TargetConfig(
                        effectiveTargetHost,
                        effectiveTargetPort,
                        effectiveTargetMode,
                        effectiveTargetSni,
                        effectiveTargetInsecure,
                        effectiveTargetVerifyHostname,
                        effectiveRewriteHostHeader,
                        mergeTls(
                                targetCert,
                                targetKey,
                                targetKeyStore,
                                targetKeyStorePassword,
                                targetTrustStore,
                                targetTrustStorePassword,
                                targetKeyStoreType,
                                targetTrustStoreType,
                                fileConfig == null || fileConfig.target() == null ? null
                                        : fileConfig.target().tls()))));
    }

    private RouteConfig routeFromFile(ConfigFile.RouteSection route) {
        if (route.listener() == null || route.target() == null) {
            throw new IllegalArgumentException("Each route requires listener and target sections");
        }
        validateRequired(route.listener().port(), route.target().host(), route.target().port());
        return new RouteConfig(
                route.id() == null || route.id().isBlank() ? "route-" + route.listener().port() : route.id(),
                new ListenerConfig(
                        select(route.listener().host(), null, "0.0.0.0"),
                        route.listener().port(),
                        enumValue(null, route.listener().mode(), TransportMode.class, TransportMode.PLAIN),
                        enumValue(null, route.listener().clientAuth(), ClientAuthMode.class, ClientAuthMode.NONE),
                        mergeTls(null, null, null, null, null, null, null, null, route.listener().tls())),
                new TargetConfig(
                        route.target().host(),
                        route.target().port(),
                        enumValue(null, route.target().mode(), TransportMode.class, TransportMode.PLAIN),
                        route.target().sni(),
                        selectBoolean(null, route.target().insecure(), false),
                        selectBoolean(null, route.target().verifyHostname(), false),
                        selectBoolean(null, route.target().rewriteHostHeader(), false),
                        mergeTls(null, null, null, null, null, null, null, null, route.target().tls())));
    }

    private ConfigFile loadConfigFile() {
        if (configFile == null) {
            return null;
        }
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config file not found: " + configFile);
        }
        try {
            return ConfigLoader.load(configFile);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Unable to load config file " + configFile + ": " + exception.getMessage(), exception);
        }
    }

    private static void validateRequired(Integer listenPort, String targetHost, Integer targetPort) {
        if (listenPort == null) {
            throw new IllegalArgumentException(
                    "Missing required value: listen port. Use --listen-port or provide listener.port in --config.");
        }
        if (targetHost == null || targetHost.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required value: target host. Use --target-host or provide target.host in --config.");
        }
        if (targetPort == null) {
            throw new IllegalArgumentException(
                    "Missing required value: target port. Use --target-port or provide target.port in --config.");
        }
    }

    private static TlsMaterial mergeTls(
            Path cert,
            Path key,
            Path keyStore,
            String keyStorePassword,
            Path trustStore,
            String trustStorePassword,
            String keyStoreType,
            String trustStoreType,
            ConfigFile.TlsSection fileTls) {
        return new TlsMaterial(
                pathValue(cert, fileTls == null ? null : fileTls.cert(), null),
                pathValue(key, fileTls == null ? null : fileTls.key(), null),
                pathValue(keyStore, fileTls == null ? null : fileTls.keyStore(), null),
                select(keyStorePassword, fileTls == null ? null : fileTls.keyStorePassword(), null),
                pathValue(trustStore, fileTls == null ? null : fileTls.trustStore(), null),
                select(trustStorePassword, fileTls == null ? null : fileTls.trustStorePassword(), null),
                select(keyStoreType, fileTls == null ? null : fileTls.keyStoreType(), "PKCS12"),
                select(trustStoreType, fileTls == null ? null : fileTls.trustStoreType(), "PKCS12"));
    }

    private static <T> T select(T cliValue, T fileValue, T defaultValue) {
        return cliValue != null ? cliValue : fileValue != null ? fileValue : defaultValue;
    }

    private static boolean selectBoolean(Boolean cliValue, Boolean fileValue, boolean defaultValue) {
        return cliValue != null ? cliValue : fileValue != null ? fileValue : defaultValue;
    }

    private static Path pathValue(Path cliValue, String fileValue, Path defaultValue) {
        return cliValue != null ? cliValue
                : fileValue != null && !fileValue.isBlank() ? Path.of(fileValue) : defaultValue;
    }

    private static <E extends Enum<E>> E enumValue(E cliValue, String fileValue, Class<E> type, E defaultValue) {
        if (cliValue != null) {
            return cliValue;
        }
        if (fileValue != null && !fileValue.isBlank()) {
            return Enum.valueOf(type, fileValue.trim().toUpperCase());
        }
        return defaultValue;
    }

    private static ConfigFile exampleConfigFile() {
        return new ConfigFile(
                null,
                new ConfigFile.ListenerSection(
                        "127.0.0.1",
                        9000,
                        "PLAIN",
                        "NONE",
                        null),
                new ConfigFile.TargetSection(
                        "jsonplaceholder.typicode.com",
                        443,
                        "TLS",
                        "jsonplaceholder.typicode.com",
                        true,
                        false,
                        true,
                        null),
                new ConfigFile.UiSection(
                        "127.0.0.1",
                        8080,
                        true),
                "./sessions",
                "NONE",
                List.of("TLSv1.3", "TLSv1.2"),
                List.of());
    }
}
