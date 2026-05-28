#!/usr/bin/env bash
# Sign each Sentinel-3 product directory listed in products.tsv as one record.
# Restart-safe via $DONE/<product-id> markers.
set -u

TOOL="${TOOL:-$HOME/trace4eo/signing-tool-linux-amd64}"
INPUT="${INPUT:-products.tsv}"
DONE="${DONE:-$HOME/trace4eo/done}"
LOG="${LOG:-$HOME/trace4eo/sign.log}"
REGISTER_URL="${REGISTER_URL:-https://124.197.43.5.nip.io/api/provenance}"
KEYCLOAK_URL="${KEYCLOAK_URL:-https://124.197.43.5.nip.io}"
PARALLEL="${PARALLEL:-6}"
TOKEN_FILE="${TOKEN_FILE:-$HOME/.sigstore-id-token}"

if [ ! -s "$TOKEN_FILE" ]; then
  echo "ERROR: $TOKEN_FILE missing. Start the signing-tool sigstore-token-daemon command first." >&2
  exit 1
fi

mkdir -p "$DONE" "$(dirname "$LOG")"

sign_one() {
  local rtype="$1" dir="$2" pid="$3"
  local marker="$DONE/$pid"
  [ -f "$marker" ] && return 0
  if [ ! -d "$dir" ]; then
    echo "MISSING $pid ($dir)" >>"$LOG"
    return 1
  fi
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null) || token=""
  if [ -z "$token" ]; then
    echo "NO_TOKEN $pid" >>"$LOG"
    return 1
  fi
  local stdout_file
  stdout_file=$(mktemp)
  if SIGSTORE_ID_TOKEN="$token" "$TOOL" create-provenance-record \
        --directory "$dir" \
        --provenance-record-type "$rtype" \
        --data-id "$pid" \
        --register-url "$REGISTER_URL" \
        --keycloak-url "$KEYCLOAK_URL" \
        --save-record false >"$stdout_file" 2>>"$LOG"; then
    cat "$stdout_file" >>"$LOG"
    local rid
    rid=$(grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' \
            "$stdout_file" | head -1)
    rm -f "$stdout_file"
    if [ -z "$rid" ]; then
      echo "NO_ID $pid ($dir)" >>"$LOG"
      return 1
    fi
    printf '%s\n' "$rid" > "$marker"
  else
    cat "$stdout_file" >>"$LOG"
    rm -f "$stdout_file"
    echo "FAIL $pid ($dir)" >>"$LOG"
    return 1
  fi
}
export -f sign_one
export TOOL DONE LOG REGISTER_URL KEYCLOAK_URL TOKEN_FILE

xargs -a "$INPUT" -d '\n' -P "$PARALLEL" -I {} bash -c '
  IFS=$'"'"'\t'"'"' read -r rtype dir pid <<<"{}"
  sign_one "$rtype" "$dir" "$pid"
'
