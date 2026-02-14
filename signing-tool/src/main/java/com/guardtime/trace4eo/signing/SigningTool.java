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
        @Option(longName = "files", description = "Files to be included in provenance record") List<String> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID") String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessor IDs (UUIDs)") List<String> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "output", description = "Output directory for ZIP file") Path outputDir,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm
    ) throws IOException {
        List<Path> paths = toPaths(files);
        HashAlgorithm algorithm = validateHashAlgorithm(hashAlgorithm);
        List<Predecessor> parsedPredecessors = toPredecessors(predecessors);
        validateInput(paths, provenanceRecordType, dataId);
        validateFilesExist(paths);
        validateOutputDirectory(outputDir);
        validateRegistrationConfig(registerUrl, keycloakUrl);

        String oidcToken = oidcTokenResolver.resolve();

        ProvenanceRecord record = buildSignedRecord(
                paths, dataId, provenanceRecordType, parsedPredecessors, algorithm, oidcToken);
        ensureDirectoryExists(outputDir);
        Path resolvedOutput = resolveOutputPath(outputDir, record.id());
        writeContainer(record, resolvedOutput);
        log.info("Provenance record saved to {}", resolvedOutput.toAbsolutePath());
        registerIfConfigured(record, registerUrl, keycloakUrl, realm, oidcToken);
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
                    throw new IllegalArgumentException("Invalid predecessor ID: " + id.trim());
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
            throw new IllegalArgumentException("Invalid --hash-algorithm: " + hashAlgorithm
                + ". Supported values: " + java.util.Arrays.stream(HashAlgorithm.values())
                    .map(Enum::name).toList());
        }
    }

    private void validateFilesExist(List<Path> files) {
        for (Path file : files) {
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("File does not exist: " + file);
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Path is not a regular file: " + file);
            }
            if (!Files.isReadable(file)) {
                throw new IllegalArgumentException("File is not readable: " + file);
            }
        }
    }

    private void validateOutputDirectory(Path outputDir) {
        if (outputDir == null) {
            return;
        }
        if (Files.exists(outputDir)) {
            if (!Files.isDirectory(outputDir)) {
                throw new IllegalArgumentException("--output is not a directory: " + outputDir);
            }
            if (!Files.isWritable(outputDir)) {
                throw new IllegalArgumentException("--output directory is not writable: " + outputDir);
            }
        } else {
            Path ancestor = outputDir.toAbsolutePath().getParent();
            while (ancestor != null && !Files.exists(ancestor)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null && !Files.isWritable(ancestor)) {
                throw new IllegalArgumentException("--output directory is not writable: " + ancestor);
            }
        }
    }

    private void registerIfConfigured(
        ProvenanceRecord record, String registerUrl,
        String keycloakUrl, String realm, String oidcToken
    ) {
        if (registerUrl == null || registerUrl.isBlank()) {
            return;
        }
        String accessToken = null;
        if (oidcToken != null) {
            accessToken = registrationClient.exchangeToken(keycloakUrl, realm, oidcToken);
        } else {
            log.warn("No OIDC token available for token exchange. Attempting registration without auth.");
        }
        List<String> failures = registrationClient.registerRecords(List.of(record), registerUrl, accessToken);
        if (!failures.isEmpty()) {
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
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(files)
            .build();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, oidcToken);
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }
}
