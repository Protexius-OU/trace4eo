# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build everything
./gradlew clean build

# Run CLI tools via Gradle
./gradlew :signing-tool:bootRun --args="<command> <options>"
./gradlew :verification-tool:bootRun --args="<command> <options>"

# Type-check frontend
cd tracing-ui && npx tsc --noEmit
```

## Architecture

Four Gradle modules (Java 21) plus a React frontend:

- **provenance** — core library, exported as API to other modules. Contains record model (`ProvenanceRecord`, `Metadata`, `Manifest`, `FilesInfo`), builder pattern for record construction, Sigstore-based signing/verification services, JSON/ZIP container I/O, and custom Jackson serializers (`ProvenanceJsonMapper`).
- **signing-tool** — Spring Shell CLI. `SigningTool` with `create-provenance-record` (single record) and `batch-sign` (batch, optional HTTP registration with Keycloak auth).
- **verification-tool** — Spring Shell CLI. `VerificationTool` with `verify` and `verify-provenance-record` commands.
- **tracing-system** — Spring Boot web app. REST API (`/api/provenance`), PostgreSQL via Flyway, Keycloak OAuth2 resource server, provenance graph traversal (BFS). Uses Testcontainers for integration tests.
- **tracing-ui** — React/TypeScript (Vite), D3 graph visualization, TanStack React Query. Built separately with npm.

## Local Dev Environment

```bash
./build-dev.sh   # builds Docker images
./start-dev.sh   # start all services via Docker Compose
./stop-dev.sh    # stop all services
```

Docker Compose runs all services: PostgreSQL (trace4eo/trace4eo), Keycloak (port 8180, brokers Sigstore for OIDC tokens), tracing-system backend (port 8080), and frontend (port 3000).

## Workflow

- Run code quality checks when you're done making a series of code changes
- After making changes to **tracing-ui**, run `cd tracing-ui && npx tsc --noEmit` to catch TypeScript errors
- Always validate user input and write unit tests respectively
- Keep top level functions as clean as possible by extracting methods
