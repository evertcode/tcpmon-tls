package com.cafeina.tcpmon.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.cafeina.tcpmon.LoggingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingConfiguratorTest {
    @AfterEach
    void resetLogging() {
        LoggingConfigurator.configure(LoggingConfig.defaults());
    }

    @Test
    void configuresRootAccessAndMetricsLoggers() {
        LoggingConfigurator.configure(new LoggingConfig("DEBUG", "json", true, true));

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertEquals(Level.DEBUG, context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel());
        assertEquals(Level.INFO, context.getLogger("com.cafeina.tcpmon.access").getLevel());
        assertEquals(Level.INFO, context.getLogger("com.cafeina.tcpmon.metrics").getLevel());
    }

    @Test
    void disablesAccessAndMetricsByDefault() {
        LoggingConfigurator.configure(LoggingConfig.defaults());

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertEquals(Level.INFO, context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel());
        assertEquals(Level.OFF, context.getLogger("com.cafeina.tcpmon.access").getLevel());
        assertEquals(Level.OFF, context.getLogger("com.cafeina.tcpmon.metrics").getLevel());
    }
}
