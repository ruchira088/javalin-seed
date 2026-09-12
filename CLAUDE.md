# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A template repository for a Java 25 HTTP API service built on Javalin 7. The single Gradle subproject `api`
contains everything; the root only holds `settings.gradle`, `gradle.properties` (all dependency versions),
Checkstyle/SpotBugs config, and deployment playbooks. See `README.md` for the template-initialisation script
(`scripts/init-template.sh`), environment variables, and endpoints.

## Commands

Requires JDK 25. The wrapper pins Gradle 9.7.1.

```bash
./gradlew build                 # compile + test + Checkstyle + SpotBugs + JaCoCo (what to run before pushing)
./gradlew check                 # static analysis + tests (CI runs `assemble` then `check`)
./gradlew test                  # tests only; JaCoCo report is generated automatically afterwards
./gradlew run                   # start the API on http://localhost:8080
./gradlew checkstyleMain spotbugsMain   # lint without running tests
./gradlew dependencyUpdates     # report newer versions of the deps pinned in gradle.properties
```

Run a single test class or method (the subproject is `:api`):

```bash
./gradlew :api:test --tests 'com.ruchij.web.RoutesTest'
./gradlew :api:test --tests 'com.ruchij.AppTest.shouldServeOpenApiSpec'
```

Reports land under `api/build/reports/` (`tests/test/`, `jacoco/test/html/`, `checkstyle/`, `spotbugs/`).

## Build constraints that will bite you

- **`-Xlint:all -Werror`** is set for every `JavaCompile` task: any compiler warning (deprecation, unchecked,
  unused varargs…) fails the build.
- **JaCoCo coverage floor**: `check` runs `jacocoTestCoverageVerification`, which fails the build if line coverage
  drops below 90% (the generated `com.ruchij.build` package is excluded). Compilation uses a Gradle JDK toolchain
  (`languageVersion = 25`), so a JDK 25 must be installed locally — Gradle auto-detects SDKMAN/Homebrew installs.
- **Checkstyle** (`config/checkstyle/checkstyle.xml`) enforces a 150-column line limit, no star imports,
  braces on all blocks, and standard naming. **SpotBugs** exclusions live in `config/spotbugs/exclude.xml`; the
  `EI_EXPOSE_REP` suppression there is intentional for records holding unmodifiable `List`s — add new record/DI
  classes to that `<Or>` block rather than copying the list defensively.
- **`generateBuildInfo`** (in `api/build.gradle`) runs before `compileJava`. It shells out to `git rev-parse`
  and writes `com.ruchij.build.BuildInfo` into `api/build/generated/src/main/java/`, which is added as a source
  dir. That generated class is excluded from Checkstyle and SpotBugs. `BuildInformation.get()` is the typed
  wrapper the rest of the code uses; never reference `BuildInfo` directly elsewhere.

## Architecture

**Bootstrap chain** (`com.ruchij.App`): `main` → `ConfigFactory.load()` → `ApplicationConfiguration.parse` →
`run` → `App.javalin(Routes, allowedOrigins)`. `App.javalin` is the seam tests use — it builds a fully configured
`Javalin` instance (virtual threads, Jackson via `JsonUtils.OBJECT_MAPPER`, CORS, OpenAPI + Swagger plugins,
routes, exception mapping) without starting it. There is no DI framework; services are constructed in
`App.routes(...)` and passed into `Routes` by constructor.

**Routing**: `Routes` is the root `EndpointGroup`; it mounts sub-groups with `path("service", serviceRoute)`.
To add an endpoint: create an `EndpointGroup` in `web/routes/`, annotate each handler with `@OpenApi` (the
`openapi-annotation-processor` turns these into the `/openapi.json` spec at compile time — un-annotated handlers
are silently missing from the spec), and mount it in `Routes.addEndpoints()`.

**Error handling**: throw one of the exceptions in `com.ruchij.exceptions` (`ValidationException` → 400,
`ResourceNotFoundException` → 404, `ResourceConflictException` → 409); `web/middleware/ExceptionMapper` converts
them to an `ErrorResponse` JSON body. Anything else becomes a 500. New exception types need a line in
`ExceptionMapper.handle(RoutesConfig)`.

**Correlation IDs**: `web/middleware/CorrelationId` (registered in `App.javalin` beside `ExceptionMapper`) reads
`X-Correlation-ID` from each request — or generates a UUID when it is absent or fails the `[A-Za-z0-9._:-]{1,64}`
allow-list, since the value is written into log lines — puts it in the SLF4J MDC as `correlationId`, stores it
as a `Context` attribute of the same name, and echoes it on the response. The Logback console pattern prints it and
the logstash encoder includes MDC, so it appears in every log line for the request. The K8s probes send this header.

**Configuration**: Typesafe Config. `application.conf` uses the `key = ${?ENV_VAR}` idiom so environment
variables override file values. Optional keys are read through `ConfigReaders.optionalConfig(...)`, which turns
`ConfigException.Missing` into `Optional.empty()`. Config is parsed into records (`ApplicationConfiguration`,
`HttpConfiguration`) up front, so the rest of the app never touches `Config`.

**Health/build info**: `GET /service/info` (`ServiceRoute` → `HealthService`) combines `BuildInformation` with
JVM properties and a `Clock`. `ApiLoggerContextListener` also reads `BuildInformation` to inject `app.name`,
`git.branch`, `git.commit`, and `APP_HOSTNAME` into the Logback context for structured (logstash) logging.

## Tests

JUnit Jupiter (BOM 6.x) + Mockito. HTTP-level tests use `JavalinTest.test(App.javalin(routes, List.of()), ...)`
with a mocked `HealthService`, so they exercise real routing, JSON, and exception mapping without a fixed port.
`api/src/test/resources/application.conf` overrides the port to `19999`; `AppTest.shouldStartServerWithMainMethod`
depends on that value.

## Deployment

`.github/workflows/build-pipeline.yml` runs on every push: `assemble` → `check` → publish Docker image (Ansible,
`playbooks/`) → deploy. Non-`main` branches deploy to **dev**; `main` deploys to **staging** then **production**.
The image (`playbooks/docker/Dockerfile.j2`) unpacks the `distTar` output (`api.tar`) and runs the
`application`-plugin start script; that script passes `-Dlogback.configurationFile=/opt/data/logback.xml`, so in
Kubernetes the Logback config comes from a mounted ConfigMap rather than the bundled `logback.xml`.
