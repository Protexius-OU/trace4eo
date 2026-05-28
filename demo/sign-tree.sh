#!/usr/bin/env bash
# Sign every group in a JSONL plan produced by discover-tree.py.
# One create-provenance-record call per group -> one record covering all the
# group's files, with the group's metadata. Restart-safe via $DONE/<group-id>.
set -u

TOOL="${TOOL:-$HOME/trace4eo/signing-tool-linux-amd64}"
INPUT="${INPUT:-plan.jsonl}"
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
if [ ! -s "$INPUT" ]; then
  echo "ERROR: plan $INPUT missing or empty." >&2
  exit 1
fi

mkdir -p "$DONE" "$(dirname "$LOG")"

sign_group() {
  local line="$1"
  local group_id data_id record_type files metadata marker token
  group_id=$(printf '%s' "$line" | python3 -c 'import json,sys;print(json.loads(sys.stdin.read())["group_id"])')
  marker="$DONE/$group_id"
  [ -f "$marker" ] && return 0

  data_id=$(printf '%s' "$line" | python3 -c 'import json,sys;print(json.loads(sys.stdin.read())["data_id"])')
  record_type=$(printf '%s' "$line" | python3 -c 'import json,sys;print(json.loads(sys.stdin.read())["record_type"])')
  files=$(printf '%s' "$line" | python3 -c 'import json,sys;print(",".join(json.loads(sys.stdin.read())["files"]))')
  metadata=$(printf '%s' "$line" | python3 -c 'import json,sys;m=json.loads(sys.stdin.read())["metadata"];print(",".join(f"{k}={v}" for k,v in m.items()))')

  if [ -z "$files" ]; then
    echo "EMPTY_GROUP $group_id" >>"$LOG"
    return 1
  fi

  token=$(cat "$TOKEN_FILE" 2>/dev/null) || token=""
  if [ -z "$token" ]; then
    echo "NO_TOKEN $group_id" >>"$LOG"
    return 1
  fi

  local stdout_file
  stdout_file=$(mktemp)
  local meta_arg=()
  [ -n "$metadata" ] && meta_arg=(--metadata "$metadata")

  if SIGSTORE_ID_TOKEN="$token" "$TOOL" create-provenance-record \
        --files "$files" \
        --provenance-record-type "$record_type" \
        --data-id "$data_id" \
        --register-url "$REGISTER_URL" \
        --keycloak-url "$KEYCLOAK_URL" \
        --save-record false \
        "${meta_arg[@]}" >"$stdout_file" 2>>"$LOG"; then
    cat "$stdout_file" >>"$LOG"
    grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' \
      "$stdout_file" | head -1 > "$marker"
    rm -f "$stdout_file"
    if [ ! -s "$marker" ]; then
      echo "NO_ID $group_id" >>"$LOG"
      rm -f "$marker"
      return 1
    fi
  else
    cat "$stdout_file" >>"$LOG"
    rm -f "$stdout_file"
    echo "FAIL $group_id" >>"$LOG"
    return 1
  fi
}
export -f sign_group
export TOOL DONE LOG REGISTER_URL KEYCLOAK_URL TOKEN_FILE

xargs -a "$INPUT" -d '\n' -P "$PARALLEL" -I {} bash -c 'sign_group "$@"' _ {}
