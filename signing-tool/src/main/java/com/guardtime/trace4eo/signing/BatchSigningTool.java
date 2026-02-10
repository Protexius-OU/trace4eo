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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class BatchSigningTool {

    private static final Logger log = LoggerFactory.getLogger(BatchSigningTool.class);

    private final ProvenanceSigningService signingService;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    @Autowired
    public BatchSigningTool(
        ProvenanceSigningService signingService,
        ProvenanceJsonMapper provenanceJsonMapper,
        RecordRegistrationClient registrationClient
    ) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    BatchSigningTool(ProvenanceSigningService signingService, ProvenanceJsonMapper provenanceJsonMapper,
                     RecordRegistrationClient registrationClient, String oidcToken) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(oidcToken);
    }

    @Command(name = "batch-sign", description = "Sign multiple files, creating one provenance record per file")
    public BatchSigningResult batchSign(
        @Option(longName = "files", description = "Files to sign") List<Path> files,
        @Option(longName = "directory", description = "Directory containing files to sign") Path directory,
        @Option(longName = "pattern", description = "Glob pattern for files in directory", defaultValue = "*") String pattern,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Base data ID (each file gets dataId/filename)") String dataId,
        @Option(longName = "output", description = "Output ZIP file path") Path outputPath,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm,
        @Option(longName = "username", description = "Keycloak username (for registration auth)") String username,
        @Option(longName = "password", description = "Keycloak password (for registration auth)") String password
    ) throws IOException {
        validateInput(provenanceRecordType, dataId, outputPath, directory);

        String oidcToken = oidcTokenResolver.resolve();
        List<Path> resolvedFiles = resolveFiles(files, directory, pattern);
        if (resolvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No files to sign. Provide --files or --directory with --pattern");
        }

        HashAlgorithm algorithm = HashAlgorithm.valueOf(hashAlgorithm);
        SigningOutcome outcome = signFiles(resolvedFiles, dataId, provenanceRecordType, algorithm, oidcToken);

        if (!outcome.records.isEmpty()) {
            writeContainer(outcome.records, outputPath);
            log.info("Written {} records to {}", outcome.records.size(), outputPath);
            registerIfConfigured(outcome.records, registerUrl, keycloakUrl, realm, username, password);
        }

        return buildResult(resolvedFiles.size(), outcome.results, outputPath);
    }

    private void validateInput(String provenanceRecordType, String dataId, Path outputPath, Path directory) {
        if (provenanceRecordType == null || provenanceRecordType.isBlank()) {
            throw new IllegalArgumentException("--provenance-record-type must not be null or blank");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("--data-id must not be null or blank");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("--output must not be null");
        }
        if (directory != null) {
            if (!Files.exists(directory)) {
                throw new IllegalArgumentException("--directory does not exist: " + directory);
            }
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("--directory is not a directory: " + directory);
            }
        }
    }

    private record SigningOutcome(List<FileSigningResult> results, List<ProvenanceRecord> records) {}

    private SigningOutcome signFiles(
        List<Path> files, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, String oidcToken
    ) {
        List<FileSigningResult> results = new ArrayList<>();
        List<ProvenanceRecord> records = new ArrayList<>();
        for (Path file : files) {
            try {
                ProvenanceRecord record = createSingleFileRecord(file, dataId, provenanceRecordType, algorithm, oidcToken);
                records.add(record);
                results.add(FileSigningResult.success(file, record.id()));
                log.info("Successfully signed: {}", file);
            } catch (Exception e) {
                log.warn("Failed to sign file: {}", file, e);
                results.add(FileSigningResult.failure(file, e.getMessage()));
            }
        }
        return new SigningOutcome(results, records);
    }

    private void registerIfConfigured(
        List<ProvenanceRecord> records, String registerUrl,
        String keycloakUrl, String realm, String username, String password
    ) {
        if (registerUrl == null || registerUrl.isBlank()) {
            return;
        }
        String accessToken = null;
        if (keycloakUrl != null && username != null && password != null) {
            accessToken = registrationClient.authenticateWithDirectGrant(keycloakUrl, realm, username, password);
        }
        List<String> failures = registrationClient.registerRecords(records, registerUrl, accessToken);
        if (!failures.isEmpty()) {
            log.warn("Registration completed with {} failure(s)", failures.size());
        }
    }

    private BatchSigningResult buildResult(int totalFiles, List<FileSigningResult> results, Path outputPath) {
        int successCount = (int) results.stream().filter(FileSigningResult::success).count();
        return new BatchSigningResult(
            totalFiles,
            successCount,
            totalFiles - successCount,
            results,
            outputPath
        );
    }

    private List<Path> resolveFiles(List<Path> files, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();

        if (files != null && !files.isEmpty()) {
            result.addAll(files);
        }

        if (directory != null) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        result.add(path);
                    }
                }
            }
        }

        return result;
    }

    private ProvenanceRecord createSingleFileRecord(
        Path file, String baseDataId, String provenanceRecordType,
        HashAlgorithm algorithm, String oidcToken
    ) throws IOException {
        String fileDataId = baseDataId + "/" + file.getFileName().toString();
        Metadata metadata = new Metadata(fileDataId, provenanceRecordType, List.of());
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(List.of(file))
            .build();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, algorithm, oidcToken);
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }

    private void writeContainer(List<ProvenanceRecord> records, Path outputPath) throws IOException {
        ProvenanceRecord headRecord = records.getLast();
        Container container = new Container(headRecord.id(), new LinkedHashSet<>(records));
        ZipContainerWriter writer = new ZipContainerWriter(provenanceJsonMapper);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            writer.writeTo(container, out);
        }
    }
}
