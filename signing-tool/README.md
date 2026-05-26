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

Create a provenance record containing multiple files with metadata.

**Options:**

| Option                     | Description                                                                                                                                                                                                       | Default           |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `--files`                  | Files to include in the record (either this or `--directory` is required)                                                                                                                                         | None              |
| `--directory`              | Directory containing files to include in the record (either this or `--files` is required)                                                                                                                        | None              |
| `--pattern`                | Glob pattern for files in `--directory`                                                                                                                                                                           | `*`               |
| `--provenance-record-type` | Type of provenance record                                                                                                                                                                                         | Required          |
| `--data-id`                | Identifier for the data                                                                                                                                                                                           | Required          |
| `--predecessors`           | UUIDs of predecessor records                                                                                                                                                                                      | None              |
| `--predecessors-file`      | Path to a plain-text file of predecessor record IDs, one UUID per line (as produced by `batch-sign --create-record-ids-file`); merged with `--predecessors`. Blank lines and lines starting with `#` are ignored. | None              |
| `--hash-algorithm`         | Hash algorithm to use                                                                                                                                                                                             | SHA256            |
| `--output`                 | Output directory for the saved record (used with `--save-record`)                                                                                                                                                 | Current directory |
| `--register-url`           | Tracing backend URL to register provenance records                                                                                                                                                                | None              |
| `--keycloak-url`           | Keycloak server URL (required when `--register-url` is set)                                                                                                                                                       | None              |
| `--realm`                  | Keycloak realm                                                                                                                                                                                                    | trace4eo          |
| `--save-record`            | Save the provenance record                                                                                                                                                                                        | true              |
| `--metadata`               | Custom metadata as `key=value` pairs, comma-separated (e.g. `env=prod,owner=alice`). The first `=` in each entry separates key from value, so values may contain `=` but not `,`.                                 | None              |

**Examples:**

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files image.tif,metadata.xml \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101"
```

<details>
<summary>Attach custom metadata inline</summary>

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files image.tif \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101 \
  --metadata env=prod,operator=alice,ticket=PROV-123"
```

</details>

<details>
<summary>Include all TIF files from a directory in a single record</summary>

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101"
```

</details>

<details>
<summary>Combine explicit files with a directory scan</summary>

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files metadata.xml \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101"
```

</details>

<details>
<summary>Create and register with a tracing system</summary>

```bash
./gradlew :signing-tool:bootRun --args="create-provenance-record \
  --files image.tif,metadata.xml \
  --provenance-record-type sentinel2-processing \
  --data-id S2A_MSIL1C_20240101 \
  --register-url http://localhost:8080/api/provenance \
  --keycloak-url http://localhost:8180"
```

</details>

### batch-sign

Sign multiple files, creating one provenance record per file.

**Options:**

| Option                     | Description                                                                                                                                                                               | Default           |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `--files`                  | Explicit list of files to sign (either this or `--directory` is required)                                                                                                                 | None              |
| `--directory`              | Directory containing files to sign (either this or `--files` is required)                                                                                                                 | None              |
| `--pattern`                | Glob pattern for files in directory                                                                                                                                                       | `*`               |
| `--provenance-record-type` | Type of provenance record                                                                                                                                                                 | Required          |
| `--data-id`                | Data ID prefix for provenance records; each file gets `<data-id>/<filename>`                                                                                                              | Required          |
| `--output`                 | Output directory for saved records (used with `--save-record`)                                                                                                                            | Current directory |
| `--hash-algorithm`         | Hash algorithm to use                                                                                                                                                                     | SHA256            |
| `--register-url`           | Tracing backend URL to register provenance records                                                                                                                                        | None              |
| `--keycloak-url`           | Keycloak server URL (required when `--register-url` is set)                                                                                                                               | None              |
| `--realm`                  | Keycloak realm                                                                                                                                                                            | trace4eo          |
| `--save-record`            | Save the provenance records                                                                                                                                                               | true              |
| `--create-record-ids-file` | Write a plain-text file with the IDs of all successfully signed provenance records, one UUID per line (written to `--output` directory, or current directory if omitted)                  | false             |
| `--threads`                | Maximum number of files to sign concurrently                                                                                                                                              | 3                 |
| `--start-index`            | Start index (inclusive) into the alphabetically-sorted file list. Use with `--end-index` to process a slice of a large directory across multiple invocations.                             | 0                 |
| `--end-index`              | End index (exclusive) into the alphabetically-sorted file list.                                                                                                                           | Total file count  |
| `--metadata`               | Custom metadata as `key=value` pairs, comma-separated; applied to every record in the batch. The first `=` in each entry separates key from value, so values may contain `=` but not `,`. | None              |

**Examples:**

Sign specific files:

```bash
./gradlew :signing-tool:bootRun --args="batch-sign \
  --files image1.tif,image2.tif,image3.tif \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output /data/output"
```

<details>
<summary>Sign all TIF files in a directory</summary>

```bash
./gradlew :signing-tool:bootRun --args="batch-sign \
  --directory /data/images \
  --pattern '*.tif' \
  --provenance-record-type satellite-imagery \
  --data-id batch-2024-01 \
  --output /data/output"
```

</details>

<details>
<summary>Sign and register with a tracing system</summary>

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

</details>

<details>
<summary>Capture record IDs for use as predecessors in a follow-up record</summary>

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

</details>

### register-records

Register provenance records with a tracing backend.

**Options:**

| Option           | Description                                                                                     | Default  |
|------------------|-------------------------------------------------------------------------------------------------|----------|
| `--records`      | Explicit list of provenance record files to register (either this or `--directory` is required) | None     |
| `--directory`    | Directory containing provenance record files (either this or `--records` is required)           | None     |
| `--pattern`      | Glob pattern for files in `--directory`                                                         | `*.zip`  |
| `--register-url` | Tracing backend URL                                                                             | Required |
| `--keycloak-url` | Keycloak server URL                                                                             | Required |
| `--realm`        | Keycloak realm                                                                                  | trace4eo |

**Examples:**

Register every zip in a directory:

```bash
./gradlew :signing-tool:bootRun --args="register-records \
  --directory /data/output \
  --register-url http://localhost:8080/api/provenance \
  --keycloak-url http://localhost:8180"
```

<details>
<summary>Register specific files</summary>

```bash
./gradlew :signing-tool:bootRun --args="register-records \
  --records /data/output/recA.zip,/data/output/recB.zip \
  --register-url http://localhost:8080/api/provenance \
  --keycloak-url http://localhost:8180"
```

</details>

### get-oidc-token

Obtain a Sigstore OIDC token interactively.

```bash
./gradlew :signing-tool:bootRun --args="get-oidc-token"
```

### sigstore-token-daemon

Perform a one-time Sigstore device-flow OIDC login, write the `id_token` to disk, then keep refreshing it before
expiry so downstream batch flows can pick up a fresh token without having to log in again.

**Options:**

| Option                   | Description                                   | Default                     |
|--------------------------|-----------------------------------------------|-----------------------------|
| `--issuer`               | Sigstore OIDC issuer                          | Discovered via Sigstore TUF |
| `--token-file`           | Path to write the `id_token` to               | `~/.sigstore-id-token`      |
| `--refresh-lead-seconds` | Refresh this many seconds before token expiry | 10                          |

The token file is written atomically with owner-only permissions (`rw-------`). If the issuer doesn't return a
refresh token, the daemon falls back to re-running the device flow on each expiry.

```bash
./gradlew :signing-tool:bootRun --args="sigstore-token-daemon"
```

<details>
<summary>Write to a custom path</summary>

```bash
./gradlew :signing-tool:bootRun --args="sigstore-token-daemon \
  --token-file /var/run/trace4eo/sigstore-id-token"
```

</details>

## Notes

- Provenance records are saved by default. Pass `--save-record false` to skip saving (e.g. when only registering).
  Use `--output` to specify a directory; if omitted, the record is saved to the current directory. If the specified
  directory doesn't exist, it will be created automatically.
- `batch-sign` always creates one provenance record per file; each file's record uses the data ID `<data-id>/<filename>`
- The `--register-url` option POSTs each provenance record as JSON to the specified URL
- When `--register-url` is used, `--keycloak-url` is required. The tool exchanges the Sigstore OIDC token for a Keycloak
  access token via RFC 8693 token exchange to authenticate with the tracing backend.
- When `--register-url` and `--predecessors` are both provided, the tool validates that all predecessor records exist
  in the tracing backend before signing. If any predecessors are missing, the command aborts with an error listing the
  missing IDs, avoiding the slow Sigstore OIDC signing flow for records that would be rejected on registration.
