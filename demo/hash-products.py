#!/usr/bin/env python3
"""Hash files listed in products.tsv into the format expected by
`verify-provenance-record --file-hashes`.

products.tsv (from list-products.py) has rows:
    <record-type>\t<product-dir>\t<product-id>

For each <product-dir>, we walk the top level only (regular files, matching
the same default --pattern '*' that sign-products.sh uses) and emit one
line per file:

    <basename>=<base64-sha256>

Output modes:
  --output-dir <dir>     one file per product: <dir>/<product-id>.hashes
                         (default; avoids basename collisions across products)
  --single-file <path>   concatenate everything into one file
                         (last-write-wins on basename collisions; only useful
                          if you are verifying one product at a time)

Records store files by basename only (FilesInfoBuilder.addFile uses
Path.getFileName()), so the file paths inside .SEN3/ collide across
products (every product has Oa01_radiance.nc, geo_coordinates.nc, ...).
That is why per-product hash files are the default.
"""
import argparse
import base64
import concurrent.futures
import fnmatch
import hashlib
import os
import sys
from pathlib import Path

CHUNK = 1 << 20


def sha256_b64(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(CHUNK), b""):
            h.update(block)
    return base64.b64encode(h.digest()).decode()


def list_product_files(product_dir: Path, pattern: str):
    for child in sorted(product_dir.iterdir()):
        if not child.is_file():
            continue
        if not fnmatch.fnmatch(child.name, pattern):
            continue
        yield child


def hash_product(record_type, product_dir, product_id, pattern):
    p = Path(product_dir)
    if not p.is_dir():
        return product_id, None, f"missing dir: {product_dir}"
    entries = []
    try:
        for child in list_product_files(p, pattern):
            entries.append(f"{child.name}={sha256_b64(child)}")
    except OSError as e:
        return product_id, None, f"read error: {e}"
    return product_id, entries, None


def read_rows(input_path):
    rows = []
    with open(input_path) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                print(f"skip malformed row: {line}", file=sys.stderr)
                continue
            rows.append(parts)
    return rows


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", default="products.tsv",
                    help="TSV: <record-type>\\t<product-dir>\\t<product-id>")
    ap.add_argument("--output-dir", default="hashes",
                    help="per-product hash files dir (default: hashes/)")
    ap.add_argument("--single-file",
                    help="write one combined file instead of per-product files")
    ap.add_argument("--pattern", default="*",
                    help="glob applied to file names in each product dir "
                         "(default: '*', matches --pattern in sign-products.sh)")
    ap.add_argument("--parallel", type=int, default=8,
                    help="products to hash in parallel (default: 8)")
    ap.add_argument("--limit", type=int, default=None,
                    help="only hash the first N products from --input")
    args = ap.parse_args()

    rows = read_rows(args.input)
    if args.limit is not None:
        rows = rows[:args.limit]
    if not rows:
        print(f"no rows in {args.input}", file=sys.stderr)
        return 1

    single = None
    if args.single_file:
        single = open(args.single_file, "w")
    else:
        os.makedirs(args.output_dir, exist_ok=True)

    done = 0
    failed = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.parallel) as ex:
        futures = [
            ex.submit(hash_product, rt, d, pid, args.pattern)
            for rt, d, pid in rows
        ]
        for fut in concurrent.futures.as_completed(futures):
            pid, entries, err = fut.result()
            done += 1
            if err is not None:
                failed += 1
                print(f"FAIL {pid}: {err}", file=sys.stderr)
                continue
            if single is not None:
                single.write(f"# product {pid}\n")
                single.write("\n".join(entries))
                single.write("\n")
            else:
                out_path = Path(args.output_dir) / f"{pid}.hashes"
                with open(out_path, "w") as out:
                    out.write("\n".join(entries))
                    out.write("\n")
            if done % 100 == 0:
                print(f"  {done}/{len(rows)} products hashed "
                      f"({failed} failed)", file=sys.stderr, flush=True)

    if single is not None:
        single.close()
    print(f"Done: {done}/{len(rows)} products hashed, {failed} failed",
          file=sys.stderr)
    return 0 if failed == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
