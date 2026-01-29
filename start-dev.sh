#!/bin/bash
set -e

# Start db and frontend in background
docker compose up -d

# Wait for database to be ready
echo "Waiting for database..."
until docker compose exec db pg_isready -U trace4eo > /dev/null 2>&1; do
  sleep 1
done
echo "Database ready"

# Run backend in foreground
./gradlew :tracing-system:bootRun
