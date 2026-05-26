#!/bin/bash
set -e

set -a && source "$(dirname "$0")/.env" && set +a

FRESH=false
SEED=false

for arg in "$@"; do
  case $arg in
    --fresh)
      FRESH=true
      ;;
    --seed)
      SEED=true
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: ./start-dev.sh [--fresh] [--seed]"
      exit 1
      ;;
  esac
done

if [ "$FRESH" = true ]; then
  echo "Starting with a fresh database (removing existing volumes)..."
  docker compose down -v
fi

docker compose up -d --wait

# Keycloak 26.6 enforces sslRequired=EXTERNAL on the master realm; port-forwarded
# host requests fail "HTTPS required". Relax it for local dev via kcadm inside the container.
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8180 --realm master \
  --user admin --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE >/dev/null

./config/keycloak/configure-token-exchange.sh
echo "All services started. Frontend at http://localhost:3000, backend at http://localhost:8080"

if [ "$SEED" = true ]; then
  "$(dirname "$0")/seed-dev.sh"
fi
