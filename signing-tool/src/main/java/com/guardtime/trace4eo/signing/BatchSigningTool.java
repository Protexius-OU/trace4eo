package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import dev.sigstore.KeylessSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BatchSigningTool {

    private static final Logger log = LoggerFactory.getLogger(BatchSigningTool.class);

    private final SigningInputValidator validator;
    private final RecordSigningService recordSigningService;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    @Autowired
    public BatchSigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        RecordRegistrationClient registrationClient
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    public BatchSigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        RecordRegistrationClient registrationClient,
        String oidcToken
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(oidcToken);
    }

    @Command(name = "batch-sign", description = "Sign multiple files, creating one provenance record per file")
    public List<UUID> batchSign(
        @Option(longName = "files", description = "Files to sign") List<String> files,
        @Option(longName = "directory", description = "Directory containing files to sign") Path directory,
        @Option(longName = "pattern", description = "Glob pattern for files in directory", defaultValue = "*") String pattern,
        @Option(longName = "provenance-record-type", description = "Provenance record type",
            required = true) String provenanceRecordType,
        @Option(longName = "data-id", description = "Base data ID (each file gets dataId/filename)",
            required = true) String dataId,
        @Option(longName = "output", description = "Output directory for ZIP file") Path outputDir,
        @Option(longName = "hash-algorithm", description = "Hash algorithm (SHA256, SHA384, SHA512)",
            defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm,
        @Option(longName = "create-record-ids-file",
            description = "Write a plain-text file with the IDs of all successfully signed provenance records, one UUID per line",
            defaultValue = "false") boolean createRecordIdsFile
    ) throws IOException {
        List<Path> filePaths = files != null ? files.stream().map(Path::of).toList() : List.of();
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        validateInput(filePaths, provenanceRecordType, dataId, directory);
        validator.validateFilesExist(filePaths);
        validator.validateOutputDirectory(outputDir);
        validator.validateGlobPattern(pattern);
        validator.validateRegistrationConfig(registerUrl, keycloakUrl);

        List<Path> resolvedFiles = resolveFiles(filePaths, directory, pattern);
        if (resolvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No files found matching the given --files or --directory/--pattern");
        }

        String oidcToken = oidcTokenResolver.resolve();
        if (oidcToken == null) {
            throw new IllegalStateException("No OIDC token available. Set SIGSTORE_ID_TOKEN or enable browser-based login.");
        }

        KeylessSigner signer = recordSigningService.buildSigner(oidcToken);
        SigningOutcome outcome = signFiles(resolvedFiles, dataId, provenanceRecordType, algorithm, signer);

        if (!outcome.records.isEmpty()) {
            Path resolvedOutput = recordSigningService.saveAll(outcome.records, outputDir, dataId);
            String accessToken = registrationClient.exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
            registrationClient.registerIfConfigured(outcome.records, registerUrl, accessToken);
            writeRecordIdsIfConfigured(outcome.results, createRecordIdsFile, resolvedOutput.toAbsolutePath().getParent());
        } else if (createRecordIdsFile) {
            log.warn("--create-record-ids-file was requested but no records were signed successfully; no file written");
        }

        return outcome.results.stream()
            .filter(FileSigningResult::success)
            .map(FileSigningResult::recordId)
            .toList();
    }

    private void validateInput(
        List<Path> files, String provenanceRecordType, String dataId, Path directory
    ) {
        if ((files == null || files.isEmpty()) && directory == null) {
            throw new IllegalArgumentException("Either --files or --directory is required");
        }
        validator.validateProvenanceRecordType(provenanceRecordType);
        validator.validateDataId(dataId);
        if (directory != null) {
            if (!Files.exists(directory)) {
                throw new IllegalArgumentException(String.format("--directory does not exist: %s", directory));
            }
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException(String.format("--directory is not a directory: %s", directory));
            }
        }
    }

    private record SigningOutcome(List<FileSigningResult> results, List<ProvenanceRecord> records) {}

    private SigningOutcome signFiles(
        List<Path> files, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, KeylessSigner signer
    ) {
        List<FileSigningResult> results = new ArrayList<>();
        List<ProvenanceRecord> records = new ArrayList<>();
        log.info("Signing {} provenance record(s)...", files.size());
        for (Path file : files) {
            try {
                String fileDataId = dataId + "/" + file.getFileName().toString();
                UnsignedRecord unsigned = recordSigningService.build(
                    List.of(file), fileDataId, provenanceRecordType, List.of(), algorithm);
                ProvenanceRecord record = recordSigningService.sign(unsigned, signer);
                records.add(record);
                results.add(FileSigningResult.success(file, record.id()));
            } catch (Exception e) {
                log.warn("Failed to create provenance record for file: {}", file, e);
                results.add(FileSigningResult.failure(file, e.getMessage()));
            }
        }
        log.info("Successfully signed {}/{} provenance records", records.size(), files.size());
        return new SigningOutcome(results, records);
    }

    private List<Path> resolveFiles(List<Path> files, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        if (files != null && !files.isEmpty()) result.addAll(files);
        if (directory != null) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) result.add(path);
                }
            }
        }
        return result;
    }

    private Path writeRecordIdsIfConfigured(
        List<FileSigningResult> results, boolean createRecordIdsFile, Path outputDir
    ) throws IOException {
        if (!createRecordIdsFile) return null;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        Path recordIdsPath = outputDir.resolve("record-ids-" + timestamp + ".txt");
        List<String> ids = results.stream()
            .filter(FileSigningResult::success)
            .map(r -> r.recordId().toString())
            .toList();
        Files.writeString(recordIdsPath, String.join("\n", ids) + "\n");
        log.info("Record IDs written to: {}", recordIdsPath);
        return recordIdsPath;
    }
}
