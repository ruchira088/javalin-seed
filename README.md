# javalin-seed

A template repository for bootstrapping a Java HTTP API service. It ships with everything you typically wire up at the start of a new service, so you can get to writing business code on day one.

## What's included

- **[Javalin](https://javalin.io/) 7** on virtual threads (Java 25), with CORS, structured exception mapping, and Jackson JSON.
- **OpenAPI** spec generation (annotation processor) plus a bundled **Swagger UI**. Spec is served at `/openapi.json`.
- **Typesafe Config** for layered configuration via `application.conf` and environment variables.
- **Logback** with [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) for JSON logs.
- **`/service/info` health endpoint** that reports build metadata (name, group, git branch/commit, build timestamp, Gradle version, JVM properties) — populated at compile time by a `generateBuildInfo` Gradle task.
- **Quality gates**: Checkstyle, SpotBugs, JaCoCo coverage, and `-Werror -Xlint:all`.
- **Tests**: JUnit 5 + Mockito, with end-to-end tests against a real Javalin instance.
- **CI/CD**: GitHub Actions pipeline (`.github/workflows/build-pipeline.yml`) and Ansible playbooks (`playbooks/`) that build a Docker image and deploy it to Kubernetes (dev / staging / production).

## Using this template

After cloning, run the one-shot initializer to rename the project:

```bash
./scripts/init-template.sh <new-project-name> ["New Display Name"]
```

- `<new-project-name>` — kebab-case (e.g. `payments-api`). Replaces every occurrence of `javalin-seed` across Gradle, Kubernetes manifests, Ansible playbooks, GitHub Actions, the Dockerfile, and tests.
- `<New Display Name>` — optional Title-Case form (e.g. `"Payments API"`). Replaces `Javalin Seed` in the OpenAPI metadata. Defaults to a Title-Cased version of the kebab name.

The script aborts if your working tree is dirty, deletes itself when finished, and is idempotent in the sense that there's nothing left to re-run. After it finishes:

```bash
./gradlew test
git add -A && git commit -m "Initialize from template"
```

The Java package `com.ruchij` and the Docker registry owner in `playbooks/tasks/build-and-publish-docker-image.yml` are intentionally **not** rewritten — change those by hand if you want to.

## Project layout

```
api/                       Application module (Gradle subproject)
  src/main/java/com/ruchij/
    App.java               Javalin bootstrap
    config/                ApplicationConfiguration (Typesafe Config)
    service/health/        Health/build-info service
    web/
      Routes.java          Top-level endpoint group
      routes/              Route handlers
      middleware/          ExceptionMapper
      responses/           Shared response shapes
    utils/                 JSON, logging utilities
  src/main/resources/      application.conf, logback.xml
  src/test/java/com/ruchij/  JUnit 5 + Mockito tests
  build.gradle             Module build (also defines generateBuildInfo)
config/                    Checkstyle and SpotBugs configuration
playbooks/                 Ansible playbooks + K8s manifests + Dockerfile.j2
scripts/init-template.sh   One-shot template initializer (deleted after use)
settings.gradle            rootProject.name lives here
gradle.properties          Centralised dependency versions
```

## Local development

Requirements: **JDK 25**.

```bash
./gradlew build           # compile + test + checkstyle + spotbugs
./gradlew test            # tests + JaCoCo report
./gradlew run             # start the API on http://localhost:8080
```

Configuration is read from `api/src/main/resources/application.conf` and overridable via environment variables:

| Variable               | Default | Notes                                       |
|------------------------|---------|---------------------------------------------|
| `HTTP_PORT`            | `8080`  | Port the server binds to.                   |
| `HTTP_ALLOWED_ORIGINS` | unset   | Comma-separated CORS origins, in addition to the localhost defaults. |

### Endpoints

- `GET /service/info` — service + build metadata.
- `GET /openapi.json` — OpenAPI 3 specification.
- `GET /swagger` — Swagger UI.

## Deployment

The repository wires up a full GitHub Actions → Docker → Kubernetes pipeline. To use it for your project:

1. Update the Docker image owner in `playbooks/tasks/build-and-publish-docker-image.yml` (the registry path defaults to `ghcr.io/ruchira088/...`).
2. Review the K8s manifests under `playbooks/k8s/` (namespace, deployment, service, ingress, certificate, autoscaler, configmaps, secrets) and the Ansible variables in `playbooks/{dev,staging,production}-deploy.yml`.
3. Configure the AWS / GHCR credentials referenced by `.github/workflows/build-pipeline.yml`.

## License

No license is bundled by default. Add one before publishing the new project.
