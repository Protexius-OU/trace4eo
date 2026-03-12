#!/bin/bash
# Configures Keycloak fine-grained permissions so the trace4eo-ui client
# can exchange external Sigstore OIDC tokens for Keycloak access tokens.
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="trace4eo"
IDP_ALIAS="sigstore"
CLIENT_ID="trace4eo-ui"

echo "Waiting for Keycloak to be ready..."
until curl -sf "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
  echo "  Keycloak not ready yet, retrying in 5s..."
  sleep 5
done

echo "Obtaining admin token..."
ADMIN_TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
  -d "grant_type=password" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

AUTH="Authorization: Bearer ${ADMIN_TOKEN}"

echo "Enabling permissions on identity provider '${IDP_ALIAS}'..."
curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/identity-provider/instances/${IDP_ALIAS}/management/permissions" \
  -H "${AUTH}" -H "Content-Type: application/json" \
  -d '{"enabled": true}' > /dev/null

echo "Looking up token-exchange permission for '${IDP_ALIAS}'..."
PERMISSIONS=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/identity-provider/instances/${IDP_ALIAS}/management/permissions" \
  -H "${AUTH}")
TOKEN_EXCHANGE_PERM_ID=$(echo "${PERMISSIONS}" | python3 -c "import sys,json; print(json.load(sys.stdin)['scopePermissions']['token-exchange'])")

echo "Looking up realm-management client internal ID..."
RM_CLIENT_ID=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=realm-management" \
  -H "${AUTH}" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

echo "Creating client policy for '${CLIENT_ID}'..."
EXISTING=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${RM_CLIENT_ID}/authz/resource-server/policy/client?name=token-exchange-for-${CLIENT_ID}" \
  -H "${AUTH}" || echo "[]")

if echo "${EXISTING}" | python3 -c "import sys,json; d=json.load(sys.stdin); d=d[0] if isinstance(d,list) and len(d)>0 else d; exit(0 if isinstance(d,dict) and d.get('id') else 1)" 2>/dev/null; then
  POLICY_ID=$(echo "${EXISTING}" | python3 -c "import sys,json; d=json.load(sys.stdin); d=d[0] if isinstance(d,list) else d; print(d['id'])")
  echo "Policy already exists: ${POLICY_ID}"
else
  curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${RM_CLIENT_ID}/authz/resource-server/policy/client" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "{\"type\":\"client\",\"name\":\"token-exchange-for-${CLIENT_ID}\",\"clients\":[\"${CLIENT_ID}\"]}" \
    > /dev/null
  POLICY_ID=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${RM_CLIENT_ID}/authz/resource-server/policy/client?name=token-exchange-for-${CLIENT_ID}" \
    -H "${AUTH}" | python3 -c "import sys,json; d=json.load(sys.stdin); d=d[0] if isinstance(d,list) else d; print(d['id'])")
  echo "Created policy: ${POLICY_ID}"
fi

echo "Associating policy with token-exchange permission..."
UPDATED=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${RM_CLIENT_ID}/authz/resource-server/permission/scope/${TOKEN_EXCHANGE_PERM_ID}" \
  -H "${AUTH}" | python3 -c "
import sys, json
p = json.load(sys.stdin)
policies = p.get('policies', [])
policy_id = '${POLICY_ID}'
if policy_id not in policies:
    policies.append(policy_id)
p['policies'] = policies
print(json.dumps(p))
")

curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${RM_CLIENT_ID}/authz/resource-server/permission/scope/${TOKEN_EXCHANGE_PERM_ID}" \
  -H "${AUTH}" -H "Content-Type: application/json" \
  -d "${UPDATED}" > /dev/null

echo "Token exchange configured: '${CLIENT_ID}' can now exchange '${IDP_ALIAS}' tokens."

echo "Making firstName and lastName optional in user profile..."
PROFILE=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users/profile" -H "${AUTH}")
UPDATED_PROFILE=$(echo "${PROFILE}" | python3 -c "
import sys, json
p = json.load(sys.stdin)
for attr in p.get('attributes', []):
    if attr['name'] in ('firstName', 'lastName'):
        attr.pop('required', None)
print(json.dumps(p))
")
curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/profile" \
  -H "${AUTH}" -H "Content-Type: application/json" \
  -d "${UPDATED_PROFILE}" > /dev/null

echo "User profile updated: firstName and lastName are now optional."
