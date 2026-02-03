# Verification Tool

A CLI tool for verifying provenance records and signatures using Sigstore.

## Building

```bash
./gradlew :verification-tool:build
```

## Running

Commands are passed via `--args`:

```bash
./gradlew :verification-tool:run --args="<command> <options>"
```

## Commands

### verify

Verify input data against a signature file.

**Options:**

| Option | Description |
|--------|-------------|
| `--text` | Path to the input file to verify |
| `--signature` | Path to the signature file |

**Example:**

```bash
./gradlew :verification-tool:run --args="verify \
  --text data.bin \
  --signature signature.json"
```

### verify-provenance-record

Verify a provenance record container. Both ZIP (`.zip`) and JSON (`.json`) container formats are accepted. This runs all verification steps for each record in the container:

- Files info hash matches manifest
- Metadata hash matches manifest
- File content hashes (if files are available)
- Signature verified against manifest

**Options:**

| Option | Description |
|--------|-------------|
| `--file` | Path to the provenance record container file |

**Example:**

```bash
./gradlew :verification-tool:run --args="verify-provenance-record \
  --file provenance.zip"
```
