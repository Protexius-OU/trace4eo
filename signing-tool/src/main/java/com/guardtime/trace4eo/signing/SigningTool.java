package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    private final ProvenanceSigningService signingService;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    @Autowired
    public SigningTool(
        ProvenanceSigningService signingService,
        ProvenanceJsonMapper provenanceJsonMapper,
        RecordRegistrationClient registrationClient
    ) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    SigningTool(ProvenanceSigningService signingService, ProvenanceJsonMapper provenanceJsonMapper,
                RecordRegistrationClient registrationClient, String oidcToken) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(oidcToken);
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public ProvenanceRecord createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record",
            required = true) List<String> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type",
            required = true) String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID",
            required = true) String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessor IDs (UUIDs)") List<String> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm (SHA256, SHA384, SHA512)",
            defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "output", description = "Output directory for ZIP file") Path outputDir,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm,
        @Option(longName = "predecessors-file",
            description = "Path to a plain-text file of predecessor record IDs, one UUID per line.")
            Path predecessorsFile
    ) throws IOException {
        List<Path> paths = toPaths(files);
        HashAlgorithm algorithm = validateHashAlgorithm(hashAlgorithm);
        validateInput(paths, provenanceRecordType, dataId);
        validateFilesExist(paths);
        validateOutputDirectory(outputDir);
        validateRegistrationConfig(registerUrl, keycloakUrl);
        validatePredecessorsFile(predecessorsFile);
        List<Predecessor> parsedPredecessors = Stream.concat(
            toPredecessors(predecessors).stream(),
            readPredecessorsFromFile(predecessorsFile).stream()
        ).toList();

        String oidcToken = oidcTokenResolver.resolve();
        String accessToken = exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
        validatePredecessorsExist(parsedPredecessors, registerUrl, accessToken);

        ProvenanceRecord record = buildSignedRecord(
                paths, dataId, provenanceRecordType, parsedPredecessors, algorithm, oidcToken);
        ensureDirectoryExists(outputDir);
        Path resolvedOutput = resolveOutputPath(outputDir, record.id());
        writeContainer(record, resolvedOutput);
        log.info("Provenance record saved to {}", resolvedOutput.toAbsolutePath());
        registerIfConfigured(record, registerUrl, accessToken);
        return record;
    }

    private List<Path> toPaths(List<String> files) {
        if (files == null) return List.of();
        return files.stream().map(Path::of).toList();
    }

    private List<Predecessor> toPredecessors(List<String> predecessors) {
        if (predecessors == null) return List.of();
        return predecessors.stream()
            .map(id -> {
                try {
                    return new Predecessor(UUID.fromString(id.trim()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format("Invalid predecessor ID: %s", id.trim()));
                }
            })
            .toList();
    }

    private void validateInput(List<Path> files, String provenanceRecordType, String dataId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("--files must not be null or empty");
        }
        if (provenanceRecordType == null || provenanceRecordType.isBlank()) {
            throw new IllegalArgumentException("--provenance-record-type must not be null or blank");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("--data-id must not be null or blank");
        }
    }

    private void validatePredecessorsFile(Path predecessorsFile) {
        if (predecessorsFile == null) return;
        if (!Files.exists(predecessorsFile)) {
            throw new IllegalArgumentException(String.format("--predecessors-file does not exist: %s", predecessorsFile));
        }
        if (!Files.isRegularFile(predecessorsFile)) {
            throw new IllegalArgumentException(String.format("--predecessors-file is not a regular file: %s", predecessorsFile));
        }
        if (!Files.isReadable(predecessorsFile)) {
            throw new IllegalArgumentException(String.format("--predecessors-file is not readable: %s", predecessorsFile));
        }
    }

    private List<Predecessor> readPredecessorsFromFile(Path predecessorsFile) {
        if (predecessorsFile == null) return List.of();
        List<String> lines;
        try {
            lines = Files.readAllLines(predecessorsFile);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot read --predecessors-file: %s", predecessorsFile), e);
        }
        List<String> ids = lines.stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
        return toPredecessors(ids);
    }

    private void validateRegistrationConfig(String registerUrl, String keycloakUrl) {
        if (registerUrl != null && !registerUrl.isBlank()
            && (keycloakUrl == null || keycloakUrl.isBlank())) {
            throw new IllegalArgumentException(
                "--keycloak-url is required when --register-url is provided");
        }
    }

    private HashAlgorithm validateHashAlgorithm(String hashAlgorithm) {
        try {
            return HashAlgorithm.valueOf(hashAlgorithm);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Invalid --hash-algorithm: %s. Supported values: %s",
                hashAlgorithm, java.util.Arrays.stream(HashAlgorithm.values()).map(Enum::name).toList()));
        }
    }

    private void validateFilesExist(List<Path> files) {
        for (Path file : files) {
            if (!Files.exists(file)) {
                throw new IllegalArgumentException(String.format("File does not exist: %s", file));
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException(String.format("Path is not a regular file: %s", file));
            }
            if (!Files.isReadable(file)) {
                throw new IllegalArgumentException(String.format("File is not readable: %s", file));
            }
        }
    }

    private void validateOutputDirectory(Path outputDir) {
        if (outputDir == null) {
            return;
        }
        if (Files.exists(outputDir)) {
            if (!Files.isDirectory(outputDir)) {
                throw new IllegalArgumentException(String.format("--output is not a directory: %s", outputDir));
            }
            if (!Files.isWritable(outputDir)) {
                throw new IllegalArgumentException(String.format("--output directory is not writable: %s", outputDir));
            }
        } else {
            Path ancestor = outputDir.toAbsolutePath().getParent();
            while (ancestor != null && !Files.exists(ancestor)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null && !Files.isWritable(ancestor)) {
                throw new IllegalArgumentException(String.format("--output directory is not writable: %s", ancestor));
            }
        }
    }

    private String exchangeTokenIfConfigured(String registerUrl, String keycloakUrl, String realm, String oidcToken) {
        if (registerUrl == null || registerUrl.isBlank()) {
            return null;
        }
        if (oidcToken != null) {
            return registrationClient.exchangeToken(keycloakUrl, realm, oidcToken);
        }
        log.warn("No OIDC token available for token exchange. Attempting registration without auth.");
        return null;
    }

    private void validatePredecessorsExist(List<Predecessor> predecessors, String registerUrl, String accessToken) {
        if (registerUrl == null || registerUrl.isBlank() || predecessors.isEmpty()) {
            return;
        }
        List<UUID> ids = predecessors.stream().map(Predecessor::id).toList();
        List<UUID> missing = registrationClient.findMissingPredecessors(ids, registerUrl, accessToken);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(String.format("Predecessor records not found: %s", missing));
        }
    }

    private void registerIfConfigured(ProvenanceRecord record, String registerUrl, String accessToken) {
        if (registerUrl == null || registerUrl.isBlank()) {
            return;
        }
        log.info("Registering provenance record to tracing system at {}...", registerUrl);
        List<String> failures = registrationClient.registerRecords(List.of(record), registerUrl, accessToken);
        if (failures.isEmpty()) {
            log.info("Successfully registered provenance record to tracing system at {}", registerUrl);
        } else {
            log.warn("Registration failed: {}", failures.getFirst());
        }
    }

    private void ensureDirectoryExists(Path outputDir) throws IOException {
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            log.info("Created output directory: {}", outputDir.toAbsolutePath());
        }
    }

    private Path resolveOutputPath(Path outputDir, UUID recordId) {
        String filename = recordId + ".zip";
        if (outputDir != null) {
            return outputDir.resolve(filename);
        }
        return Path.of(filename);
    }

    private void writeContainer(ProvenanceRecord record, Path outputPath) throws IOException {
        Container container = new Container(record.id(), new LinkedHashSet<>(Set.of(record)));
        ZipContainerWriter writer = new ZipContainerWriter(provenanceJsonMapper);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            writer.writeTo(container, out);
        }
    }

    private ProvenanceRecord buildSignedRecord(
        List<Path> files, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, HashAlgorithm algorithm, String oidcToken
    ) throws IOException {
        log.info("Creating provenance record for {} file(s)...", files.size());
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(files)
            .build();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        log.info("Signing provenance record...");
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, oidcToken);
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }
}
