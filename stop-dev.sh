#!/bin/bash
set -e

# Stops all services. Named volumes (database data) are preserved.
# To also remove volumes and start fresh, use: ./start-dev.sh --fresh
docker compose down
