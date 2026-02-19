#!/bin/bash
set -e

FRESH=false

for arg in "$@"; do
  case $arg in
    --fresh)
      FRESH=true
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: ./start-dev.sh [--fresh]"
      exit 1
      ;;
  esac
done

if [ "$FRESH" = true ]; then
  echo "Starting with a fresh database (removing existing volumes)..."
  docker compose down -v
fi

docker compose up -d --wait
./config/keycloak/configure-token-exchange.sh
echo "All services started. Frontend at http://localhost:3000, backend at http://localhost:8080"
