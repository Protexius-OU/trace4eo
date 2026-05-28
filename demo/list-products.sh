#!/usr/bin/env bash
# Enumerate Sentinel-3 product .SEN3 directories the rcrop pipeline consumes.
# Output: products.tsv with rows  <record-type>\t<product-dir>\t<product-id>
#
# Requires: curl, jq
set -euo pipefail

URL="https://stac.dataspace.copernicus.eu/v1/search"
BBOX='[14.0, 49.0, 25.0, 55.0]'
DTR='2020-01-01T00:00:00Z/2024-12-31T23:59:59Z'
MAX_CLOUD=50
OUT="products.tsv"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# (record-type, collection) pairs
COLLECTIONS=(
  "sentinel-3:sentinel-3-sl-2-lst-ntc"
  "sentinel-3:sentinel-3-syn-2-v10-ntc"
)

: > "$OUT"
> "$TMP/seen"   # dedup product dirs across collections

for entry in "${COLLECTIONS[@]}"; do
  rtype="${entry%%:*}"
  coll="${entry#*:}"
  echo "Querying $coll ..." >&2

  # Initial request body
  body=$(jq -nc --arg coll "$coll" --arg dtr "$DTR" --argjson bbox "$BBOX" --argjson cc "$MAX_CLOUD" '{
    collections:[$coll], bbox:$bbox, datetime:$dtr,
    query:{"eo:cloud_cover":{lte:$cc}}, limit:1000
  }')

  page=0
  total=0
  while : ; do
    page=$((page + 1))
    resp="$TMP/resp.json"
    curl -fsS -X POST "$URL" -H "Content-Type: application/json" -d "$body" -o "$resp"

    n=$(jq '.features | length' "$resp")
    if [ "$n" -eq 0 ]; then
      break
    fi
    total=$((total + n))

    # For each item: derive unique .SEN3 dirs from s3://eodata asset hrefs
    jq -r --arg rt "$rtype" '
      .features[] as $f
      | ($f.assets | to_entries[]
         | .value.href
         | select(startswith("s3://eodata/"))
         | sub("^s3://eodata/"; "/eodata/")
         | select(contains(".SEN3/"))
         | sub("/[^/]+$"; ""))
        as $dir
      | "\($rt)\t\($dir)\t\($f.id)"
    ' "$resp" | sort -u >> "$TMP/page.tsv"

    # Filter out duplicates we've already emitted (across pages/collections)
    awk -F'\t' 'NR==FNR{seen[$2]=1; next} !seen[$2]{print; seen[$2]=1}' \
        "$TMP/seen" "$TMP/page.tsv" > "$TMP/new.tsv"
    cat "$TMP/new.tsv" >> "$OUT"
    cut -f2 "$TMP/new.tsv" >> "$TMP/seen"
    : > "$TMP/page.tsv"

    echo "  page $page: total items=$total cumulative dirs=$(wc -l < "$OUT")" >&2

    # Next page (POST link with body)
    next_body=$(jq -c '.links[]? | select(.rel=="next") | .body // empty' "$resp")
    if [ -z "$next_body" ] || [ "$next_body" = "null" ]; then
      break
    fi
    body="$next_body"
  done
done

echo "Wrote $(wc -l < "$OUT") product directories to $OUT" >&2
