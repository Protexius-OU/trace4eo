#!/bin/bash
set -e

docker compose up -d --wait
./config/keycloak/configure-token-exchange.sh
echo "All services started. Frontend at http://localhost:3000, backend at http://localhost:8080"
