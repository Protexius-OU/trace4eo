#!/usr/bin/env python3
"""Enrich a discover-tree.py JSONL plan with country metadata.

For every line that has an `mgrs` metadata field, computes the tile's
centroid (lat/lon) and reverse-geocodes it to a country.
Writes country-code (ISO2), country (full name when pycountry is
available), latitude, and longitude into the group's metadata.

Dependencies (pip install):
    mgrs                 MGRS string -> lat/lon
    reverse_geocoder     offline lat/lon -> country code lookup
    pycountry            optional, used for the human-readable country name

Tiles without an `mgrs` field pass through unchanged.
"""
import argparse
import json
import sys
from pathlib import Path

try:
    import mgrs as mgrs_lib
except ImportError:
    sys.exit("ERROR: pip install mgrs")
try:
    import reverse_geocoder as rg
except ImportError:
    sys.exit("ERROR: pip install reverse_geocoder")
try:
    import pycountry
except ImportError:
    pycountry = None


def tile_centroid(mgrs_tile, converter):
    """Return (lat, lon) for the centroid of an S2 MGRS tile like T32UQD."""
    code = mgrs_tile[1:] if mgrs_tile.startswith("T") else mgrs_tile
    # Append a 50km easting + 50km northing to point at the centre of the 100km square.
    return converter.toLatLon(f"{code}5000050000")


def country_name(iso2):
    if pycountry is None or not iso2:
        return None
    rec = pycountry.countries.get(alpha_2=iso2)
    return rec.name if rec else None


def collect_centroids(lines):
    """First pass: compute centroids for every line with an mgrs field."""
    converter = mgrs_lib.MGRS()
    centroids = {}
    for idx, entry in enumerate(lines):
        tile = entry.get("metadata", {}).get("mgrs")
        if not tile:
            continue
        try:
            lat, lon = tile_centroid(tile, converter)
        except Exception as e:
            print(f"WARN {tile}: {e}", file=sys.stderr)
            continue
        centroids[idx] = (lat, lon)
    return centroids


def batch_reverse_geocode(centroids):
    """Single rg.search call for all coords — O(1M lookups/sec via KDTree."""
    if not centroids:
        return {}
    indices = list(centroids.keys())
    coords = [centroids[i] for i in indices]
    results = rg.search(coords, mode=1)
    return {idx: res for idx, res in zip(indices, results)}


def enrich(entry, centroid, geocode):
    lat, lon = centroid
    iso2 = geocode.get("cc", "")
    entry["metadata"]["latitude"] = f"{lat:.4f}"
    entry["metadata"]["longitude"] = f"{lon:.4f}"
    if iso2:
        entry["metadata"]["country-code"] = iso2
        name = country_name(iso2)
        if name:
            # `location` is the key the tracing-ui map filters on
            # (tracing-ui/src/features/locations/components/LocationRecordsModal.tsx).
            entry["metadata"]["location"] = name
    return entry


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, default=Path("plan.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("plan-enriched.jsonl"))
    args = parser.parse_args()

    if not args.input.is_file():
        raise SystemExit(f"--input not found: {args.input}")

    with args.input.open() as f:
        lines = [json.loads(line) for line in f if line.strip()]

    centroids = collect_centroids(lines)
    geocodes = batch_reverse_geocode(centroids)

    enriched = 0
    with args.output.open("w") as out:
        for idx, entry in enumerate(lines):
            if idx in centroids and idx in geocodes:
                enrich(entry, centroids[idx], geocodes[idx])
                enriched += 1
            out.write(json.dumps(entry) + "\n")

    print(f"{enriched}/{len(lines)} groups enriched -> {args.output}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
