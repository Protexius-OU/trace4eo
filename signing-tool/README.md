# Signing Tool

A CLI tool for creating and signing provenance records using Sigstore.

## Building

```bash
./gradlew :signing-tool:build
```

Build a standalone fat JAR:

```bash
./gradlew :signing-tool:bootJar
```

## Running

Via Gradle:

```bash
./gradlew :signing-tool:bootRun --args="<command> <options>"
```

Or as a standalone JAR:

```bash
java -jar signing-tool/build/libs/signing-tool-0.1.0-SNAPSHOT.jar <command> <options>
```

## Commands

### create-provenance-record

Create a provenance record containing multiple files with metadata. The record is saved as a ZIP container.

**Options:**

| Option                     | Description                                                                                                                                                                                                       | Default           |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `--files`                  | Files to include in the record                                                                                                                                                                                    | Required          |
| `--provenance-record-type` | Type of provenance record                                                                                                                                                                                         | Required          |
| `--data-id`                | Identifier for the data                                                                                                                                                                                           | Required          |
| `--predecessors`           | UUIDs of predecessor records                                                                                                                                                                                      | None              |
| `--predecessors-file`      | Path to a plain-text file of predecessor record IDs, one UUID per line (as produced by `batch-sign --create-record-ids-file`); merged with `--predecessors`. Blank lines and lines starting with `#` are ignored. | None              |
| `--hash-algorithm`         | Hash algorithm to use                                                                                                                                                                                             | SHA256            |
| `--output`                 | Output directory for ZIP file                                                                                                                                                                                     | Current directory |
| `--register-url`           | Tracing backend URL to register provenance records                                                                                                                                                                | None              |
| `--keycloak-url`           | Keycloak server URL (required when `--register-url` is set)                                                                                                                                                       | None              |
| `--realm`                  | Keycloak realm                                                                                                                                                                                                    | trace4eo          |

**Examples:**

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files image.tif,metadata.xml \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101"
```

Create and register with a tracing system:

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files image.tif,metadata.xml \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101 \
  --register-url http://localhost:8080/api/provenance \
  --keycloak-url http://localhost:8180"
```

### batch-sign

Sign multiple files into a single provenance record packaged as a ZIP container.
Use `--one-record-per-file` to create a separate provenance record for each file instead.

**Options:**

| Option | Description                                                                                                                                                                                          | Default           |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `--files`                  | Explicit list of files to sign (either this or `--directory` is required)                                                                                                                            | None              |
| `--directory`              | Directory containing files to sign (either this or `--files` is required)                                                                                                                            | None              |
| `--pattern`                | Glob pattern for files in directory                                                                                                                                                                  | `*`               |
| `--provenance-record-type` | Type of provenance record                                                                                                                                                                            | Required          |
| `--data-id`                | Data ID for the provenance record; with `--one-record-per-file` each file gets `<data-id>/<filename>`                                                                                                | Required          |
| `--output`                 | Output directory for ZIP file                                                                                                                                                                        | Current directory |
| `--hash-algorithm`         | Hash algorithm to use                                                                                                                                                                                | SHA256            |
| `--register-url`           | Tracing backend URL to register provenance records                                                                                                                                                   | None              |
| `--keycloak-url`           | Keycloak server URL (required when `--register-url` is set)                                                                                                                                          | None              |
| `--realm`                  | Keycloak realm                                                                                                                                                                                       | trace4eo          |
| `--create-record-ids-file` | Write a plain-text file alongside the ZIP container with the IDs of all successfully signed provenance records, one UUID per line (written to `--output` directory, or current directory if omitted) | false             |
| `--one-record-per-file`    | Create one provenance record per file instead of a single combined record                                                                                                                            | false             |

**Examples:**

Sign specific files:

```bash
./gradlew :signing-tool:bootRun --args="batch-sign \
  --files image1.tif,image2.tif,image3.tif \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output /data/output"
```

Sign all TIF files in a directory:

```bash
./gradlew :signing-tool:bootRun --args="batch-sign \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output /data/output"
```

Sign and register with a tracing system:

```bash
./gradlew :signing-tool:bootRun --args="batch-sign \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output /data/output \
  --register-url http://localhost:8080/api/provenance \
  --keycloak-url http://localhost:8180"
```

Capture record IDs for use as predecessors in a follow-up record:

```bash
# Step 1: sign the source files and capture their record IDs
./gradlew :signing-tool:bootRun --args="batch-sign \
  --directory /data/raw \
  --pattern '*.tif' \
  --provenance-record-type raw-imagery \
  --data-id batch-2024-01 \
  --output /data/output \
  --create-record-ids-file"

# Step 2: create a derived record referencing the batch records as predecessors
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files /data/processed/result.tif \
  --provenance-record-type processed-imagery \
  --data-id processed-2024-01 \
  --predecessors-file /data/output/record-ids-1708300000000.txt \
  --output /data/output"
```

## Notes

- Provenance records are always saved as ZIP containers. The output filename is auto-generated (`<record-uuid>.zip` for
  `create-provenance-record`, `<data-id>.zip` for `batch-sign`). Use `--output` to specify a directory; if omitted, the
  file is written to the current directory. If the specified directory doesn't exist, it will be created automatically.
- By default `batch-sign` combines all files into a single provenance record. With `--one-record-per-file` each file gets its own record with data ID `<data-id>/<filename>`
- The `--register-url` option POSTs each provenance record as JSON to the specified URL
- When `--register-url` is used, `--keycloak-url` is required. The tool exchanges the Sigstore OIDC token for a Keycloak
  access token via RFC 8693 token exchange to authenticate with the tracing backend.
- When `--register-url` and `--predecessors` are both provided, the tool validates that all predecessor records exist
  in the tracing backend before signing. If any predecessors are missing, the command aborts with an error listing the
  missing IDs, avoiding the slow Sigstore OIDC signing flow for records that would be rejected on registration.
