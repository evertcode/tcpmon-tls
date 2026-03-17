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

    @Option(names = "--sessions-dir")
    private Path sessionsDir;

    @Option(names = "--ui-enabled", fallbackValue = "true", arity = "0..1")
    private Boolean uiEnabled;

    @Option(names = "--ui-host")
    private String uiHost;

    @Option(names = "--ui-port")
    private Integer uiPort;

    @Option(names = "--ui-token", description = "Bearer token required on all /api/* requests. If omitted, auth is disabled.")
    private String uiToken;

    @Option(names = "--ui-tls-keystore", description = "Path to keystore file to enable HTTPS for the control plane UI.")
    private Path uiTlsKeystore;

    @Option(names = "--ui-tls-keystore-password", description = "Password for the UI TLS keystore.")
    private String uiTlsKeystorePassword;

    @Option(names = "--ui-tls-keystore-type", description = "Keystore type for the UI TLS keystore (default: PKCS12).")
    private String uiTlsKeystoreType;

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
        String effectiveUiToken = select(uiToken,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().apiToken(), null);
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

        TlsMaterial effectiveUiTls = resolveUiTlsMaterial(fileConfig);

        return new ProxyConfig(
                new UiConfig(effectiveUiHost, effectiveUiPort, effectiveUiEnabled, effectiveUiToken, effectiveUiTls),
                effectiveSessionsDir,
                effectiveInterceptMode,
                effectiveProtocols,
                effectiveCiphers);
    }

    private TlsMaterial resolveUiTlsMaterial(ConfigFile fileConfig) {
        Path keystorePath = uiTlsKeystore != null ? uiTlsKeystore
                : fileConfig != null && fileConfig.ui() != null && fileConfig.ui().tlsKeystore() != null
                        ? Path.of(fileConfig.ui().tlsKeystore())
                        : null;
        if (keystorePath == null) {
            return null;
        }
        String password = select(uiTlsKeystorePassword,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().tlsKeystorePassword(),
                null);
        String type = select(uiTlsKeystoreType,
                fileConfig == null ? null : fileConfig.ui() == null ? null : fileConfig.ui().tlsKeystoreType(),
                "PKCS12");
        return new TlsMaterial(null, null, keystorePath, password, null, null, type, null);
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
                new ConfigFile.UiSection(
                        "127.0.0.1",
                        8080,
                        true,
                        null,
                        null,
                        null,
                        null),
                "./sessions",
                "NONE",
                List.of("TLSv1.3", "TLSv1.2"),
                List.of());
    }
}
