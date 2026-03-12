#!/bin/bash
set -euo pipefail

ENV_FILE=".env"

if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: ${ENV_FILE} not found. Copy .env.example to .env and fill in your values."
  exit 1
fi

# Load variables for envsubst
set -a
# shellcheck source=.env
source "${ENV_FILE}"
set +a

echo "Generating Keycloak realm config for host: ${VM_HOST}..."
envsubst < config/keycloak/trace4eo-realm.deploy.json.tmpl > config/keycloak/trace4eo-realm.deploy.json

echo "Pulling latest images..."
docker compose -f docker-compose-deploy.yml pull

echo "Starting all services..."
docker compose -f docker-compose-deploy.yml up -d --wait

echo "Configuring Keycloak token exchange..."
KEYCLOAK_URL="http://localhost:8180" ./config/keycloak/configure-token-exchange.sh

echo ""
echo "Deployment complete."
echo "  Frontend: http://${VM_HOST}"
echo "  Keycloak: http://${VM_HOST}:8180"
