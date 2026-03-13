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

CERT_DIR="config/nginx/certs"
CERTBOT_DIR="config/certbot"
mkdir -p "${CERT_DIR}" "${CERTBOT_DIR}"
if [ ! -f "${CERT_DIR}/cert.pem" ]; then
  echo "Obtaining Let's Encrypt certificate for ${VM_HOST}..."
  docker run --rm \
    -p 80:80 \
    -v "$(pwd)/${CERTBOT_DIR}:/etc/letsencrypt" \
    certbot/certbot certonly \
    --standalone \
    --non-interactive \
    --agree-tos \
    --email "${ADMIN_EMAIL}" \
    --no-eff-email \
    -d "${VM_HOST}"
  cp "${CERTBOT_DIR}/live/${VM_HOST}/fullchain.pem" "${CERT_DIR}/cert.pem"
  cp "${CERTBOT_DIR}/live/${VM_HOST}/privkey.pem" "${CERT_DIR}/key.pem"
  echo "Certificate obtained and installed."
fi

echo "Generating Keycloak realm config for host: ${VM_HOST}..."
envsubst < config/keycloak/trace4eo-realm.deploy.json.template > config/keycloak/trace4eo-realm.deploy.json

echo "Pulling latest images..."
docker compose -f docker-compose-deploy.yml pull

echo "Starting all services..."
docker compose -f docker-compose-deploy.yml up -d --wait

echo "Configuring Keycloak token exchange..."
KEYCLOAK_URL="http://localhost:8180" ./config/keycloak/configure-token-exchange.sh

echo ""
echo "Deployment complete."
echo "  Frontend: https://${VM_HOST}"
echo "  Keycloak: https://${VM_HOST}/realms/trace4eo"
