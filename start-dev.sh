#!/bin/bash
set -e

# Start services in background and wait for healthchecks
docker compose up -d --wait

# Run backend in foreground
./gradlew :tracing-system:bootRun
