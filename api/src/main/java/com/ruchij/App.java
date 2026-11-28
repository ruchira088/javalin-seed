package com.ruchij;

import com.ruchij.config.ApplicationConfiguration;
import com.ruchij.config.HttpConfiguration;
import com.ruchij.metrics.MetricsRegistry;
import com.ruchij.service.health.HealthServiceImpl;
import com.ruchij.service.health.models.BuildInformation;
import com.ruchij.utils.JsonUtils;
import com.ruchij.web.Routes;
import com.ruchij.web.json.RequestBodyJsonMapper;
import com.ruchij.web.middleware.CorrelationId;
import com.ruchij.web.middleware.ExceptionMapper;
import com.ruchij.web.middleware.RequestLogging;
import com.ruchij.web.middleware.RequestMetrics;
import com.ruchij.web.middleware.SecurityHeaders;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.javalin.Javalin;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Properties;

public class App {
    static final String PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        Config config = ConfigFactory.load();
        ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.parse(config);

        run(applicationConfiguration);
    }

    public static void run(ApplicationConfiguration applicationConfiguration) {
        Properties properties = System.getProperties();
        Clock clock = Clock.systemUTC();
        BuildInformation buildInformation = BuildInformation.get();

        PrometheusMeterRegistry meterRegistry = MetricsRegistry.create(buildInformation.name());

        Routes routes = routes(applicationConfiguration, properties, buildInformation, clock);

        Javalin app = javalin(routes, applicationConfiguration.httpConfiguration(), meterRegistry);
        app.start(applicationConfiguration.httpConfiguration().port());

        Javalin managementApp = managementJavalin(meterRegistry);
        managementApp.start(applicationConfiguration.managementConfiguration().port());

        logger.info(
            "Server is listening on port {} (management on port {})...",
            applicationConfiguration.httpConfiguration().port(),
            applicationConfiguration.managementConfiguration().port()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            app.stop();
            managementApp.stop();
            logger.info("Server has been shut down.");
        }));
    }

    /**
     * {@link #javalin(Routes, HttpConfiguration, MeterRegistry)} for tests: the given CORS origins, no CORS credentials,
     * API docs on, and request metrics recorded into a throwaway registry. The port is unused.
     */
    public static Javalin javalin(Routes routes, List<String> allowedOrigins) {
        return javalin(routes, new HttpConfiguration(0, allowedOrigins, false, true), new SimpleMeterRegistry());
    }

    /** Builds the fully configured, unstarted API server; {@code httpConfiguration.port()} is not used here. */
    public static Javalin javalin(Routes routes, HttpConfiguration httpConfiguration, MeterRegistry meterRegistry) {
        RequestMetrics requestMetrics = new RequestMetrics(meterRegistry);
        List<String> allowedOrigins = httpConfiguration.allowedOrigins();

        return Javalin.create(javalinConfig -> {
            javalinConfig.concurrency.useVirtualThreads = true;
            javalinConfig.jsonMapper(new RequestBodyJsonMapper(JsonUtils.OBJECT_MAPPER));

            // wait 7 seconds for existing requests to finish
            javalinConfig.jetty.modifyServer(server -> server.setStopTimeout(7_000));

            // The CORS plugin rejects an empty origin list; with none configured, cross-origin requests are refused
            if (!allowedOrigins.isEmpty()) {
                javalinConfig.bundledPlugins.enableCors(cors -> {
                    cors.addRule(rule -> {
                        allowedOrigins.forEach(rule::allowHost);

                        rule.allowCredentials = httpConfiguration.allowCredentials();
                    });
                });
            }

            if (httpConfiguration.apiDocsEnabled()) {
                javalinConfig.registerPlugin(new OpenApiPlugin(openApiConfig ->
                    openApiConfig
                        .withDocumentationPath("/openapi.json")
                        .withDefinitionConfiguration((version, definition) ->
                            definition.info(info ->
                                info.title("Javalin Seed API")
                                    .version(BuildInformation.get().gitCommit())
                                    .description("API documentation for the Javalin Seed project")
                            )
                        )
                ));

                javalinConfig.registerPlugin(new SwaggerPlugin(swaggerConfig ->
                    swaggerConfig.documentationPath = "/openapi.json"
                ));
            }

            javalinConfig.routes.apiBuilder(routes);
            CorrelationId.register(javalinConfig.routes);
            SecurityHeaders.register(javalinConfig.routes);
            ExceptionMapper.handle(javalinConfig.routes);
            javalinConfig.requestLogger.http((context, executionTimeMs) -> {
                RequestLogging.log(context, executionTimeMs);
                requestMetrics.record(context, executionTimeMs);
            });
        });
    }

    /**
     * The internal server for operational endpoints. It is deliberately bare (no CORS, OpenAPI or request logging) and
     * is bound to its own port so Prometheus can scrape it without {@code /metrics} being reachable from the Ingress.
     */
    public static Javalin managementJavalin(PrometheusMeterRegistry meterRegistry) {
        return Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.get("/metrics", context -> context.contentType(PROMETHEUS_CONTENT_TYPE).result(meterRegistry.scrape()));
        });
    }

    private static Routes routes(
        ApplicationConfiguration applicationConfiguration,
        Properties properties,
        BuildInformation buildInformation,
        Clock clock
    ) {
        HealthServiceImpl healthService = new HealthServiceImpl(clock, properties, buildInformation);

        return new Routes(healthService);
    }
}
