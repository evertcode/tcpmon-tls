package com.cafeina.tcpmon.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.cafeina.tcpmon.LoggingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingConfigurator {
    private static final String TEXT_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger{36} - %msg%n";

    private LoggingConfigurator() {
    }

    public static void configure(LoggingConfig config) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        String format = normalizeFormat(config.format());
        Level rootLevel = parseLevel(config.level());
        configureRootAppender(context, createLayout(context, format), rootLevel);
        configureNamedLogger(context, "com.cafeina.tcpmon.access", config.accessLog() ? Level.INFO : Level.OFF);
        configureNamedLogger(context, "com.cafeina.tcpmon.metrics", config.metricsLog() ? Level.INFO : Level.OFF);
    }

    private static Layout<ILoggingEvent> createLayout(LoggerContext context, String format) {
        if ("json".equals(format)) {
            JsonLogLayout layout = new JsonLogLayout();
            layout.setContext(context);
            layout.start();
            return layout;
        }
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(TEXT_PATTERN);
        layout.start();
        return layout;
    }

    private static void configureNamedLogger(LoggerContext context, String name, Level level) {
        ch.qos.logback.classic.Logger logger = context.getLogger(name);
        logger.detachAndStopAllAppenders();
        logger.setAdditive(true);
        logger.setLevel(level);
    }

    private static void configureRootAppender(LoggerContext context, Layout<ILoggingEvent> layout, Level rootLevel) {
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.start();

        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName("CONSOLE");
        appender.setEncoder(encoder);
        appender.start();

        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(appender);
        root.setLevel(rootLevel);
    }

    private static Level parseLevel(String level) {
        return switch (level == null ? "INFO" : level.trim().toUpperCase()) {
            case "TRACE" -> Level.TRACE;
            case "DEBUG" -> Level.DEBUG;
            case "INFO" -> Level.INFO;
            case "WARN" -> Level.WARN;
            case "ERROR" -> Level.ERROR;
            default -> throw new IllegalArgumentException("Invalid log level: " + level);
        };
    }

    private static String normalizeFormat(String format) {
        String normalized = format == null ? "text" : format.trim().toLowerCase();
        if (!normalized.equals("text") && !normalized.equals("json")) {
            throw new IllegalArgumentException("Invalid log format: " + format + " (expected text or json)");
        }
        return normalized;
    }

    private static final class JsonLogLayout extends LayoutBase<ILoggingEvent> {
        private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

        @Override
        public String doLayout(ILoggingEvent event) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("ts", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            fields.put("level", event.getLevel().toString());
            fields.put("thread", event.getThreadName());
            fields.put("logger", event.getLoggerName());
            fields.put("message", event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                fields.put("throwable", ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            try {
                return MAPPER.writeValueAsString(fields) + System.lineSeparator();
            } catch (JsonProcessingException exception) {
                return "{\"level\":\"ERROR\",\"logger\":\"" + LoggingConfigurator.class.getName()
                        + "\",\"message\":\"Unable to encode log event\"}" + System.lineSeparator();
            }
        }
    }
}
