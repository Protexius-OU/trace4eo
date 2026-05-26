#!/usr/bin/env bash
# Populate the local Docker deployment with sample provenance records.
#
# Structure:
#   tier 1: two batches of 50 records each
#   tier 2: two records, one per tier-1 batch
#   tier 3: one record referring to both tier-2 records
#   tier 4: one record referring to tier 3
#   tier 5: one record referring to tier 4
#
# Requires:
#   - Local docker deployment running (./start-dev.sh)
#   - signing-tool sigstore-token-daemon command running with a fresh token in ~/.sigstore-id-token

set -euo pipefail

REGISTER_URL=http://localhost:8080/api/provenance
KEYCLOAK_URL=http://localhost:8180
TOKEN_FILE=$HOME/.sigstore-id-token
WORK_DIR=build/seed-data

if [ ! -s "$TOKEN_FILE" ]; then
  echo "ERROR: $TOKEN_FILE missing or empty. Start the signing-tool sigstore-token-daemon command first." >&2
  exit 1
fi

echo "Building signing-tool jar..."
./gradlew --quiet --console=plain :signing-tool:bootJar
JAR=$(ls -t signing-tool/build/libs/signing-tool-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "ERROR: signing-tool jar not found under signing-tool/build/libs" >&2
  exit 1
fi

# Re-read the token from disk each call so we always use whatever the daemon
# last wrote — Sigstore id_tokens only live ~60s.
run_tool() {
  SIGSTORE_ID_TOKEN="$(<"$TOKEN_FILE")" java -jar "$JAR" "$@"
}

capture_uuid() {
  local out
  out=$(run_tool "$@")
  printf '%s' "$out" \
    | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' \
    | head -1
}

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/batch1" "$WORK_DIR/batch2" \
         "$WORK_DIR/tier2a" "$WORK_DIR/tier2b" \
         "$WORK_DIR/tier3" "$WORK_DIR/tier4" "$WORK_DIR/tier5"

echo "Generating dummy files..."
for i in $(seq 1 50); do
  printf 'batch1 item %d\n' "$i" > "$WORK_DIR/batch1/item-$i.txt"
  printf 'batch2 item %d\n' "$i" > "$WORK_DIR/batch2/item-$i.txt"
done
echo 'tier2a aggregate' > "$WORK_DIR/tier2a/aggregate.txt"
echo 'tier2b aggregate' > "$WORK_DIR/tier2b/aggregate.txt"
echo 'tier3 merge'      > "$WORK_DIR/tier3/merge.txt"
echo 'tier4 derived'    > "$WORK_DIR/tier4/derived.txt"
echo 'tier5 derived'    > "$WORK_DIR/tier5/derived.txt"

common_opts=(
  --register-url "$REGISTER_URL"
  --keycloak-url "$KEYCLOAK_URL"
  --save-record false
)

echo "Tier 1: signing batch 1 (50 records)..."
run_tool batch-sign \
  --directory "$WORK_DIR/batch1" \
  --provenance-record-type seed-tier1-batch1 \
  --data-id seed/tier1/batch1 \
  --output "$WORK_DIR/batch1" \
  --create-record-ids-file true \
  --metadata "location=Estonia" \
  "${common_opts[@]}"

echo "Tier 1: signing batch 2 (50 records)..."
run_tool batch-sign \
  --directory "$WORK_DIR/batch2" \
  --provenance-record-type seed-tier1-batch2 \
  --data-id seed/tier1/batch2 \
  --output "$WORK_DIR/batch2" \
  --create-record-ids-file true \
  --metadata "location=Estonia" \
  "${common_opts[@]}"

BATCH1_IDS="$WORK_DIR/batch1/seed_tier1_batch1-record-ids.txt"
BATCH2_IDS="$WORK_DIR/batch2/seed_tier1_batch2-record-ids.txt"

echo "Tier 2: aggregating batch 1..."
TIER2A_ID=$(capture_uuid create-provenance-record \
  --files "$WORK_DIR/tier2a/aggregate.txt" \
  --provenance-record-type seed-tier2 \
  --data-id seed/tier2/a \
  --predecessors-file "$BATCH1_IDS" \
  --metadata "location=Germany" \
  "${common_opts[@]}")
echo "  -> $TIER2A_ID"

echo "Tier 2: aggregating batch 2..."
TIER2B_ID=$(capture_uuid create-provenance-record \
  --files "$WORK_DIR/tier2b/aggregate.txt" \
  --provenance-record-type seed-tier2 \
  --data-id seed/tier2/b \
  --predecessors-file "$BATCH2_IDS" \
  --metadata "location=Germany" \
  "${common_opts[@]}")
echo "  -> $TIER2B_ID"

echo "Tier 3: merging tier-2 records..."
TIER3_ID=$(capture_uuid create-provenance-record \
  --files "$WORK_DIR/tier3/merge.txt" \
  --provenance-record-type seed-tier3 \
  --data-id seed/tier3 \
  --predecessors "$TIER2A_ID,$TIER2B_ID" \
  --metadata "location=France" \
  "${common_opts[@]}")
echo "  -> $TIER3_ID"

echo "Tier 4: deriving from tier 3..."
TIER4_ID=$(capture_uuid create-provenance-record \
  --files "$WORK_DIR/tier4/derived.txt" \
  --provenance-record-type seed-tier4 \
  --data-id seed/tier4 \
  --predecessors "$TIER3_ID" \
  --metadata "location=France" \
  "${common_opts[@]}")
echo "  -> $TIER4_ID"

echo "Tier 5: deriving from tier 4..."
TIER5_ID=$(capture_uuid create-provenance-record \
  --files "$WORK_DIR/tier5/derived.txt" \
  --provenance-record-type seed-tier5 \
  --data-id seed/tier5 \
  --predecessors "$TIER4_ID" \
  --metadata "location=France" \
  "${common_opts[@]}")
echo "  -> $TIER5_ID"

echo
echo "Seed complete:"
echo "  tier 1: 2x50 records (location=Estonia)"
echo "  tier 2: $TIER2A_ID, $TIER2B_ID (location=Germany)"
echo "  tier 3: $TIER3_ID (location=France)"
echo "  tier 4: $TIER4_ID (location=France)"
echo "  tier 5: $TIER5_ID (location=France)"
