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

### verify

Verify input data against a signature file.

**Options:**

| Option        | Description                      |
|---------------|----------------------------------|
| `--text`      | Path to the input file to verify |
| `--signature` | Path to the signature file       |

**Example:**

```bash
./gradlew :verification-tool:bootRun --args="verify \
  --text data.bin \
  --signature signature.json"
```

### verify-provenance-record

Verify a provenance record container. Both ZIP (`.zip`) and JSON (`.json`) container formats are accepted. This runs all
verification steps for each record in the container:

- Files info hash matches manifest
- Metadata hash matches manifest
- File content hashes (if files are available)
- Signature verified against manifest

**Options:**

| Option          | Description                                                            |
|-----------------|------------------------------------------------------------------------|
| `--file`        | Path to the provenance record container file                           |
| `--file-hash`   | Single file hash as `<path>=<base64>` (optional)                       |
| `--file-hashes` | Path to a hash file with one `<path>=<base64>` per line (optional) |

Both `--file-hash` and `--file-hashes` are optional and can be combined; their entries are merged before verification.
Files in the record for which no hash is provided are skipped.

**Example — inline (single file):**

```bash
./gradlew :verification-tool:bootRun --args="verify-provenance-record \
  --file provenance.zip \
  --file-hash data.csv=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
```

**Example — hash file (multiple files):**

```bash
./gradlew :verification-tool:bootRun --args="verify-provenance-record \
  --file provenance.zip \
  --file-hashes hashes.txt"
```

**Hash file format (`hashes.txt`):**

```
# optional comments
data.csv=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
results.csv=n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=
```

### Computing file hashes

The CLI expects hashes in standard Base64 encoding, using the same algorithm that was used when signing the record (
default: SHA-256). Use `openssl` to produce the correct format:

```bash
openssl dgst -sha256 -binary data.csv | base64
# 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
```

To generate a hash file for multiple files:

```bash
for f in data.csv results.csv; do
  echo "$f=$(openssl dgst -sha256 -binary "$f" | base64)"
done > hashes.txt
```
