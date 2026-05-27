# trace4eo

trace4eo is an open-source framework for recording, signing, and verifying the
**provenance and traceability of Earth Observation (EO) data products** —
including AI-based processing stages and model-related artifacts.

It follows a *capture–sign–register–verify* methodology that decouples provenance
recording from the EO pipelines themselves, so existing producers can be
instrumented without modification:

- **Capture** — each processing stage emits a structured provenance record (stable
  identifier, data type, declared predecessors, domain-specific attributes, and a
  manifest of cryptographic hashes over its output files).
- **Sign** — the canonicalised manifest is signed *keyless* via Sigstore: the
  producer authenticates through an OIDC identity provider, obtains a short-lived
  certificate bound to that identity, and signs the manifest digest.
- **Register** — the signature and a transparency-log inclusion proof are embedded
  in a self-contained signed container, which can also be registered with the
  tracing service (queryable REST API + graph view across predecessor links).
- **Verify** — any third party can independently re-hash the data, re-derive the
  manifest, and verify the signature against the certificate and transparency log.

The project consists of multiple software components:

* Provenance SDK
* Tracing system
* Tracing UI
* Signing CLI tool
* Verification CLI tool

## Prerequisites

* Java 25
* For UI, `npm` and `node`

## Building & testing

```bash
# Build all Java modules — includes Checkstyle, SpotBugs, and tests
./gradlew clean build

# Run the CLI tools
./gradlew :signing-tool:bootRun --args="<command> <options>"
./gradlew :verification-tool:bootRun --args="<command> <options>"

# Frontend: type-check and build
cd tracing-ui && npx tsc --noEmit && npm run build
```

## Running via Docker

Build the images, then start all services (PostgreSQL, Keycloak, backend, frontend):

```bash
./build-dev.sh   # build all modules + backend and frontend Docker images
./start-dev.sh   # start all services
```

`start-dev.sh` accepts optional flags:

- `--fresh` — start with a clean database. Volumes are otherwise preserved across `./stop-dev.sh` / `./start-dev.sh` cycles.
- `--seed` — populate the deployment with sample provenance records once the services are up.

Seeding requires a Sigstore OIDC token. Before seeding, start the token daemon in a separate terminal — it performs a
one-time browser login, then keeps the token refreshed at `~/.sigstore-id-token`:

```bash
./gradlew :signing-tool:bootRun --args="sigstore-token-daemon"
```

With the daemon running, seed either during startup or against an already-running stack:

```bash
./start-dev.sh --seed   # seed as part of startup
./seed-dev.sh           # or seed a stack that is already running
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

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.
