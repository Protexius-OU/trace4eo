# trace4eo

The project consists of multiple software components:

* Provenance SDK
* Tracing system
* Tracing UI
* Signing CLI tool
* Verification CLI tool

## Prerequisites

* Java 21
* For UI, `npm` and `node`

## Running via Docker

Currently Postgres DB and frontend are run in Docker containers and backend locally, as the Sigstore signing process needs a callback URL which wouldn't be accessible if it was running inside Docker.

Use `./build-dev.sh` to build the frontend and Docker images, then `./start-dev.sh` to start the database and frontend containers and run the backend locally.

### Building a container image for the tracing system

```bash
./gradlew :tracing-system:bootBuildImage
```

This uses Spring Boot's built-in Buildpacks support to produce an OCI image.

## Static code analysis

### Checkstyle (http://checkstyle.sourceforge.net/)

Checkstyle is used to maintain a consistent code style.
Checkstyle is configured to run automatically in Gradle compile phase.

### SpotBugs (https://spotbugs.github.io/)

SpotBugs analyzes bytecode to find common bugs and code problems. This is done automatically when
running `./gradlew build`

XML and HTML reports can be found in each module's `build/reports/spotbugs/` directory.

### OpenRewrite (https://docs.openrewrite.org/)

Run `./gradlew rewriteRun` to automatically fix some checkstyle errors.

#### OWASP Dependency-Check

Dependency-Check is a utility that identifies project dependencies
and checks if there are any known, publicly disclosed, vulnerabilities.

To run the analysis, configure `dependencyCheck.nvd.apiKey` in your local `gradle.properties` file and run `./gradlew dependencyCheckAnalyze`
