# trace4eo

The project consists of multiple software components:

* Provenance SDK
* Tracing system
* Tracing UI
* Signing CLI tool
* Verification CLI tool

## Prerequisites

* Java 25
* For UI, `npm` and `node`

## Running via Docker

Use `./build-dev.sh` to build the backend image, frontend, and Docker images. Then `./start-dev.sh` to start all
services (PostgreSQL, Keycloak, backend, frontend).

Database volumes are preserved across `./stop-dev.sh` / `./start-dev.sh` cycles. To start with a clean database, pass
the `--fresh` flag:

```bash
./start-dev.sh --fresh
```

To populate the deployment with sample provenance records (two tier-1 batches of 50, two tier-2 aggregates, and a
tier-3/4/5 chain), first start the Sigstore OIDC token daemon in a separate terminal — it performs a one-time
browser login and then keeps refreshing it, stored on path `~/.sigstore-id-token`:

```bash
./gradlew :signing-tool:bootRun --args="sigstore-token-daemon"
```

Then either pass `--seed` to `start-dev.sh`, or run `./seed-dev.sh` directly after the services are up:

```bash
./start-dev.sh --seed
./seed-dev.sh                 # equivalent, when services are already running
```

### Building a container image for the tracing system

```bash
./gradlew :tracing-system:bootBuildImage
```

This uses Spring Boot's built-in Buildpacks support to produce an OCI image.

### Access control

Three roles are defined in the `trace4eo` realm:

| Role       | Permissions                                         |
|------------|-----------------------------------------------------|
| `viewer`   | Read and verify provenance records (default)        |
| `signer`   | Sign provenance records                             |
| `uploader` | Register provenance records with the tracing system |

All users authenticated via Sigstore get `viewer` by default. Signing and registering are independent: an account needs
both `signer` and `uploader` to do both in one step.

`signer` and `uploader` are granted by either:

**1. Domain allowlist** — set `SIGNER_ALLOWED_DOMAINS` in `.env` to a comma-separated list of email domain suffixes. Any
Sigstore user whose email matches is automatically granted both `signer` and `uploader`:

```
SIGNER_ALLOWED_DOMAINS=@esa.int,@example.com
```

**2. Manual assignment** — log into the Keycloak admin console at `https://<VM_HOST>/admin`, navigate to the `trace4eo`
realm → Users → select user → Role mapping, and assign the desired realm role(s).

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

To run the analysis, configure `dependencyCheck.nvd.apiKey` in your local `gradle.properties` file and run
`./gradlew dependencyCheckAnalyze`
