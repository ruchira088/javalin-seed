package com.ruchij.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.ruchij.service.health.models.BuildInformation;

public class ApiLoggerContextListener extends ContextAwareBase implements LoggerContextListener {
    @Override
    public boolean isResetResistant() {
        return true;
    }

    @Override
    public void onStart(LoggerContext context) {
        BuildInformation buildInformation = BuildInformation.create();
        context.putProperty("app.name", buildInformation.name());
        context.putProperty("app.version", buildInformation.version());
        context.putProperty("git.branch", buildInformation.gitBranch());
        context.putProperty("git.commit", buildInformation.gitCommit());

        String appHostname = System.getenv().getOrDefault("APP_HOSTNAME", "unknown");
        context.putProperty("app.hostname", appHostname);
    }

    @Override
    public void onReset(LoggerContext context) {

    }

    @Override
    public void onStop(LoggerContext context) {

    }

    @Override
    public void onLevelChange(Logger logger, Level level) {

    }
}
