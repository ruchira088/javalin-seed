package com.ruchij.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ApiLoggerContextListenerTest {

    @Test
    void shouldBeResetResistant() {
        ApiLoggerContextListener listener = new ApiLoggerContextListener();

        assertTrue(listener.isResetResistant());
    }

    @Test
    void shouldSetContextPropertiesOnStart() {
        ApiLoggerContextListener listener = new ApiLoggerContextListener();
        LoggerContext context = new LoggerContext();

        listener.onStart(context);

        assertEquals("javalin-seed", context.getProperty("app.name"));
        assertTrue(context.getProperty("git.branch") != null);
        assertTrue(context.getProperty("git.commit") != null);
        assertEquals("unknown", context.getProperty("app.hostname"));
    }

    @Test
    void shouldHandleOnResetWithoutError() {
        ApiLoggerContextListener listener = new ApiLoggerContextListener();
        LoggerContext context = new LoggerContext();

        listener.onReset(context);
    }

    @Test
    void shouldHandleOnStopWithoutError() {
        ApiLoggerContextListener listener = new ApiLoggerContextListener();
        LoggerContext context = new LoggerContext();

        listener.onStop(context);
    }

    @Test
    void shouldHandleOnLevelChangeWithoutError() {
        ApiLoggerContextListener listener = new ApiLoggerContextListener();
        Logger logger = mock(Logger.class);

        listener.onLevelChange(logger, Level.INFO);
    }
}
