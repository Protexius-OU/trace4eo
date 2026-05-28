#!/usr/bin/env python3
"""Enumerate Sentinel-3 product .SEN3 directories the rcrop pipeline consumes.

Writes one TSV row per unique product directory:
    <record-type>\t<product-dir>\t<product-id>
"""
import json
import os.path
import sys
import urllib.request

URL = "https://stac.dataspace.copernicus.eu/v1/search"
BBOX = [14.0, 49.0, 25.0, 55.0]
DTR = "2020-01-01T00:00:00Z/2024-12-31T23:59:59Z"
MAX_CLOUD = 50

QUERIES = [
    ("sentinel-3", "sentinel-3-sl-2-lst-ntc"),   # LST
    ("sentinel-3", "sentinel-3-syn-2-v10-ntc"),  # NDVI
]


def product_dir(href: str):
    """Map s3://eodata/.../<id>.SEN3/<file> -> /eodata/.../<id>.SEN3"""
    if not href.startswith("s3://eodata/"):
        return None
    local = "/eodata/" + href[len("s3://eodata/"):]
    return os.path.dirname(local) if ".SEN3/" in local else None


def search(collection):
    body = {
        "collections": [collection],
        "bbox": BBOX,
        "datetime": DTR,
        "query": {"eo:cloud_cover": {"lte": MAX_CLOUD}},
        "limit": 1000,
    }
    while True:
        req = urllib.request.Request(
            URL,
            method="POST",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=60) as r:
            d = json.load(r)
        for f in d.get("features", []):
            yield f
        nxt = next((l for l in d.get("links", []) if l.get("rel") == "next"), None)
        if not nxt or not d.get("features"):
            return
        body = nxt.get("body", body)


def main():
    seen = set()
    with open("products.tsv", "w") as out:
        for record_type, collection in QUERIES:
            n = 0
            for item in search(collection):
                dirs = {
                    product_dir(a.get("href", ""))
                    for a in item.get("assets", {}).values()
                }
                dirs.discard(None)
                for d in dirs:
                    if d in seen:
                        continue
                    seen.add(d)
                    out.write(f"{record_type}\t{d}\t{item['id']}\n")
                    n += 1
            print(f"{collection}: {n} new product dirs", file=sys.stderr)
    print(f"Total: {len(seen)} unique product directories -> products.tsv",
          file=sys.stderr)


if __name__ == "__main__":
    main()
