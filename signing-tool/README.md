# Signing Tool

A CLI tool for creating and signing provenance records using Sigstore.

## Building

```bash
./gradlew :signing-tool:build
```

## Running

Commands are passed via `--args`:

```bash
./gradlew :signing-tool:run --args="<command> <options>"
```

## Commands

### create-provenance-record

Create a provenance record containing multiple files with metadata.

**Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--files` | Files to include in the record | Required |
| `--provenance-record-type` | Type of provenance record | Required |
| `--data-id` | Identifier for the data | Required |
| `--predecessors` | IDs of predecessor records | None |
| `--hash-algorithm` | Hash algorithm to use | SHA-256 |

**Example:**

```bash
./gradlew :signing-tool:run --args="create-provenance-record \
  --files image.tif,metadata.xml \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101"
```

### batch-sign

Sign multiple files, creating one provenance record per file and packaging them into a ZIP container.

**Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--files` | Explicit list of files to sign | None |
| `--directory` | Directory containing files to sign | None |
| `--pattern` | Glob pattern for files in directory | `*` |
| `--provenance-record-type` | Type of provenance record | Required |
| `--data-id` | Base data ID (files get `<data-id>/<filename>`) | Required |
| `--output` | Output ZIP file path | Required |
| `--hash-algorithm` | Hash algorithm to use | SHA256 |
| `--register-url` | URL to POST provenance records for registration | None |

**Examples:**

Sign specific files:

```bash
./gradlew :signing-tool:run --args="batch-sign \
  --files image1.tif,image2.tif,image3.tif \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output provenance.zip"
```

Sign all TIF files in a directory:

```bash
./gradlew :signing-tool:run --args="batch-sign \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output provenance.zip"
```

Sign and register with a tracing system:

```bash
./gradlew :signing-tool:run --args="batch-sign \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output provenance.zip \
  --register-url http://localhost:8080/api/provenance"
```

## Notes

- Batch signing is limited to 100 files per invocation
- Each file in a batch gets its own provenance record with data ID `<base-id>/<filename>`
- The `--register-url` option POSTs each provenance record as JSON to the specified URL
