#!/usr/bin/env python3
"""Walk an eodata tree of arbitrary depth and emit a JSONL signing plan.

One JSON object per logical group (one batch-sign invocation), with:
    group_id     stable name for the group (derived from path)
    data_id      data-id passed to batch-sign
    record_type  provenance-record-type passed to batch-sign
    metadata     dict of key=value metadata applied to every file in the group
    files        absolute file paths included in the group

Groups are formed by walking down to a configurable depth under --root.
Every file at or below that depth is bundled into its ancestor directory's
group. This handles trees where leaf files sit at unknown depths.

MGRS-style tile names (T<dd><L><LL>) in the group path are auto-detected
and added to metadata as `mgrs`, `utm-zone`, `lat-band`.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

MGRS_RE = re.compile(r"^T(\d{2})([A-Z])([A-Z]{2})$")


def parse_kv_list(values):
    out = {}
    for raw in values or []:
        for entry in raw.split(","):
            entry = entry.strip()
            if not entry:
                continue
            if "=" not in entry:
                raise SystemExit(f"--metadata entry must be key=value: {entry}")
            k, v = entry.split("=", 1)
            k = k.strip()
            if not k:
                raise SystemExit(f"--metadata key must not be blank: {entry}")
            if k in out:
                raise SystemExit(f"duplicate --metadata key: {k}")
            out[k] = v
    return out


def enrich_with_mgrs(name, metadata):
    m = MGRS_RE.match(name)
    if not m:
        return metadata
    enriched = dict(metadata)
    enriched.setdefault("mgrs", name)
    enriched.setdefault("utm-zone", m.group(1))
    enriched.setdefault("lat-band", m.group(2))
    return enriched


def group_dirs_at_depth(root, depth, max_per_parent=None, skip_per_parent=0):
    root = root.resolve()
    if depth == 0:
        yield root
        return
    stack = [(root, 0)]
    while stack:
        directory, level = stack.pop()
        try:
            children = sorted(p for p in directory.iterdir() if p.is_dir())
        except (PermissionError, OSError) as e:
            print(f"WARN cannot list {directory}: {e}", file=sys.stderr)
            continue
        if level + 1 == depth:
            if skip_per_parent:
                children = children[skip_per_parent:]
            if max_per_parent is not None:
                children = children[:max_per_parent]
            yield from children
        else:
            stack.extend((child, level + 1) for child in children)


def collect_files(directory, include_globs, exclude_globs):
    matches = []
    for current, _, filenames in os.walk(directory):
        for name in filenames:
            path = Path(current) / name
            if include_globs and not any(path.match(g) for g in include_globs):
                continue
            if exclude_globs and any(path.match(g) for g in exclude_globs):
                continue
            matches.append(str(path))
    matches.sort()
    return matches


def build_group(group_dir, root, args, base_metadata):
    rel = group_dir.relative_to(root)
    group_id = str(rel).replace(os.sep, "_")
    files = collect_files(group_dir, args.include, args.exclude)
    if not files:
        return None
    metadata = dict(base_metadata)
    metadata.setdefault("source-path", str(group_dir))
    metadata = enrich_with_mgrs(group_dir.name, metadata)
    return {
        "group_id": group_id,
        "data_id": f"{args.data_id_prefix.rstrip('/')}/{group_id}",
        "record_type": args.record_type,
        "metadata": metadata,
        "files": files,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", required=True, type=Path,
                        help="root directory to walk")
    parser.add_argument("--output", type=Path, default=Path("plan.jsonl"),
                        help="output JSONL path (default: plan.jsonl)")
    parser.add_argument("--record-type", required=True,
                        help="provenance-record-type for every group")
    parser.add_argument("--data-id-prefix", required=True,
                        help="data-id prefix; each group becomes <prefix>/<group-id>")
    parser.add_argument("--group-depth", type=int, default=1,
                        help="depth (from root) at which to form groups (default: 1)")
    parser.add_argument("--max-per-parent", type=int, default=None,
                        help="cap groups taken from each parent dir at group-depth "
                             "(e.g. depth=2, max-per-parent=50 -> <=50 tiles per UTM zone)")
    parser.add_argument("--skip-per-parent", type=int, default=0,
                        help="skip the first N groups from each parent dir before applying "
                             "max-per-parent (e.g. skip=50 max=50 -> tiles 51..100 per zone)")
    parser.add_argument("--include", action="append", default=[],
                        help="file glob to include (repeatable); default: all files")
    parser.add_argument("--exclude", action="append", default=[],
                        help="file glob to exclude (repeatable)")
    parser.add_argument("--metadata", action="append", default=[],
                        help="constant key=value metadata; comma-separated or repeatable")
    args = parser.parse_args()

    if not args.root.is_dir():
        raise SystemExit(f"--root is not a directory: {args.root}")
    if args.group_depth < 0:
        raise SystemExit("--group-depth must be >= 0")
    if args.max_per_parent is not None and args.max_per_parent <= 0:
        raise SystemExit("--max-per-parent must be > 0")
    if args.skip_per_parent < 0:
        raise SystemExit("--skip-per-parent must be >= 0")

    base_metadata = parse_kv_list(args.metadata)
    root = args.root.resolve()

    groups = 0
    total_files = 0
    with args.output.open("w") as out:
        for group_dir in group_dirs_at_depth(root, args.group_depth,
                                              args.max_per_parent, args.skip_per_parent):
            entry = build_group(group_dir, root, args, base_metadata)
            if entry is None:
                continue
            out.write(json.dumps(entry) + "\n")
            out.flush()
            groups += 1
            total_files += len(entry["files"])
            if groups % 100 == 0:
                print(f"  {groups} groups, {total_files} files so far "
                      f"(last: {entry['group_id']})",
                      file=sys.stderr, flush=True)
    print(f"{groups} group(s), {total_files} file(s) -> {args.output}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
