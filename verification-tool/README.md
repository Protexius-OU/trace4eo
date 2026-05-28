# Verification Tool

A CLI tool for verifying provenance records and signatures using Sigstore.

## Building

```bash
./gradlew :verification-tool:build
```

## Running

Commands are passed via `--args`:

```bash
./gradlew :verification-tool:bootRun --args="<command> <options>"
```

## Commands

### verify-provenance-record

Verify a provenance record container — typically a `.zip` downloaded from the UI, though raw `.json` containers are
also accepted. For each record the tool runs:

- Files info hash matches manifest
- Metadata hash matches manifest
- File content hashes (when hashes are provided via `--file-hash` / `--file-hashes`)
- Signature verified against manifest

After all records are processed, a summary lists the totals: records verified, passed, failed, and (when
`--data-id` is set) skipped.

**Options:**

| Option          | Description                                                                                  |
|-----------------|----------------------------------------------------------------------------------------------|
| `--file`        | Path to the provenance record container file                                                 |
| `--data-id`     | Verify only records with this `metadata.dataId` (default: every record in the container)     |
| `--file-hash`   | Single file hash as `<path>=<base64>` (optional)                                             |
| `--file-hashes` | Path to a hash file with one `<path>=<base64>` per line (optional)                           |
| `--format`      | Output format: `text` (default) or `json`                                                    |
| `--silent`      | Suppress output for passing records (the summary is still printed)                           |

`--file-hash` and `--file-hashes` can be combined; their entries are merged before verification.

For multi-record containers (e.g. a chain ZIP downloaded from the UI), pass `--data-id` to pick the records you want
to verify. The data-id is the `metadata.dataId` field — typically the product name. It is not enforced unique, so
multiple records sharing a data-id will all be verified.

**Verify a downloaded record, including file contents:**

```bash
./gradlew :verification-tool:bootRun --args="verify-provenance-record \
  --file provenance.zip \
  --file-hash data.csv=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
```

**Verify a single record from a large provenance chain:**

```bash
./gradlew :verification-tool:bootRun --args="verify-provenance-record \
  --file chain.zip --data-id S3A_SR_2_LAN_LI_20231231T120000_... --file-hashes hashes.txt"
```

Hash file format (one `<path>=<base64>` per line; `#` starts a comment):

```
data.csv=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
results.csv=n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=
```

## Computing file hashes

Hashes are expected in standard Base64, using the algorithm the record was signed with (default: SHA-256).

```bash
# single file
openssl dgst -sha256 -binary data.csv | base64

# whole directory -> hashes.txt
for f in /test-data/*; do
  echo "$(basename "$f")=$(openssl dgst -sha256 -binary "$f" | base64)"
done > hashes.txt
```
