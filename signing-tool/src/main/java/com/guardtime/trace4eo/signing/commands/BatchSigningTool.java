package com.guardtime.trace4eo.signing.commands;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.signing.OidcTokenResolver;
import com.guardtime.trace4eo.signing.OutputWriter;
import com.guardtime.trace4eo.signing.RecordSigningService;
import com.guardtime.trace4eo.signing.UnsignedRecord;
import com.guardtime.trace4eo.signing.registration.RecordRegistrationClient;
import dev.sigstore.KeylessSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BatchSigningTool {

    private static final Logger log = LoggerFactory.getLogger(BatchSigningTool.class);

    private final SigningInputValidator validator;
    private final RecordSigningService recordSigningService;
    private final OutputWriter outputWriter;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    public BatchSigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        OutputWriter outputWriter,
        RecordRegistrationClient registrationClient,
        OidcTokenResolver oidcTokenResolver
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.outputWriter = outputWriter;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = oidcTokenResolver;
    }

    @Command(name = "batch-sign", description = "Sign multiple files, creating one provenance record per file")
    public List<UUID> batchSign(
        @Option(longName = "files", description = "Files to sign") List<String> files,
        @Option(longName = "directory", description = "Directory containing files to sign") Path directory,
        @Option(longName = "pattern", description = "Glob pattern for files in directory", defaultValue = "*") String pattern,
        @Option(longName = "provenance-record-type", description = "Provenance record type",
            required = true) String provenanceRecordType,
        @Option(longName = "data-id",
            description = "Data ID prefix for provenance records; each file gets <data-id>/<filename>",
            required = true) String dataId,
        @Option(longName = "output", description = "Output directory for ZIP file") Path outputDir,
        @Option(longName = "hash-algorithm", description = "Hash algorithm (SHA256, SHA384, SHA512)",
            defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm,
        @Option(longName = "create-record-ids-file",
            description = "Write a plain-text file with the IDs of all successfully signed provenance records, one UUID per line",
            defaultValue = "false") boolean createRecordIdsFile,
        @Option(longName = "threads",
            description = "Maximum concurrent signing threads (default: 4)",
            defaultValue = "4") int threads
    ) throws IOException, InterruptedException {
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        List<Path> resolvedFiles = validateAndResolveFiles(files, directory, pattern, provenanceRecordType,
            dataId, outputDir, registerUrl, keycloakUrl);
        String oidcToken = resolveOidcToken();
        List<ProvenanceRecord> records = signFilesIndividually(resolvedFiles, dataId, provenanceRecordType, algorithm,
            oidcToken, Math.max(1, threads));
        List<UUID> recordIds = records.stream().map(ProvenanceRecord::id).toList();
        if (!records.isEmpty()) {
            outputWriter.saveAll(records, outputDir, dataId);
            registerRecords(records, registerUrl, keycloakUrl, realm);
            if (createRecordIdsFile) {
                outputWriter.writeRecordIds(recordIds, outputDir, dataId);
            }
        } else if (createRecordIdsFile) {
            log.warn("--create-record-ids-file was requested but no records were signed successfully; no file written");
        }
        return recordIds;
    }

    private List<Path> validateAndResolveFiles(
        List<String> files, Path directory, String pattern, String provenanceRecordType,
        String dataId, Path outputDir, String registerUrl, String keycloakUrl
    ) throws IOException {
        List<Path> filePaths = files != null ? files.stream().map(Path::of).toList() : List.of();
        validateInput(filePaths, provenanceRecordType, dataId, directory);
        validator.validateFilesExist(filePaths);
        validator.validateOutputDirectory(outputDir);
        validator.validateGlobPattern(pattern);
        validator.validateRegistrationConfig(registerUrl, keycloakUrl);

        List<Path> resolvedFiles = resolveFiles(filePaths, directory, pattern);
        if (resolvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No files found matching the given --files or --directory/--pattern");
        }
        return resolvedFiles;
    }

    private String resolveOidcToken() {
        String oidcToken = oidcTokenResolver.resolve();
        if (oidcToken == null) {
            throw new IllegalStateException("No OIDC token available. Set SIGSTORE_ID_TOKEN or enable browser-based login.");
        }
        return oidcToken;
    }

    private void registerRecords(
        List<ProvenanceRecord> records, String registerUrl, String keycloakUrl, String realm
    ) {
        String oidcToken = oidcTokenResolver.resolve();
        String accessToken = registrationClient.exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
        registrationClient.registerIfConfigured(records, registerUrl, accessToken);
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

    private List<ProvenanceRecord> signFilesIndividually(
        List<Path> files, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, String oidcToken, int threads
    ) throws InterruptedException {
        int total = files.size();
        log.info("Signing {} file(s) with up to {} concurrent thread(s)...", total, threads);
        AtomicInteger completedCount = new AtomicInteger(0);
        Semaphore semaphore = new Semaphore(threads);
        List<Future<Optional<ProvenanceRecord>>> futures = new ArrayList<>(total);

        ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().factory());
        try {
            progressScheduler.scheduleAtFixedRate(
                () -> log.info("Progress: {}/{} signed...", completedCount.get(), total),
                30, 30, TimeUnit.SECONDS);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Path file : files) {
                    futures.add(executor.submit(() -> {
                        semaphore.acquire();
                        try {
                            String fileDataId = dataId + "/" + file.getFileName();
                            KeylessSigner signer = recordSigningService.buildSigner(oidcToken);
                            UnsignedRecord unsigned = recordSigningService.build(
                                List.of(file), fileDataId, provenanceRecordType, List.of(), algorithm);
                            return Optional.of(recordSigningService.sign(unsigned, signer));
                        } catch (Exception e) {
                            log.warn("Failed to create provenance record for file: {}", file, e);
                            return Optional.<ProvenanceRecord>empty();
                        } finally {
                            semaphore.release();
                            completedCount.incrementAndGet();
                        }
                    }));
                }
            }
        } finally {
            progressScheduler.shutdownNow();
        }

        List<ProvenanceRecord> records = new ArrayList<>(futures.size());
        for (Future<Optional<ProvenanceRecord>> future : futures) {
            try {
                future.get().ifPresent(records::add);
            } catch (ExecutionException e) {
                log.warn("Unexpected signing task failure", e.getCause());
            }
        }
        log.info("Successfully signed {}/{} provenance records", records.size(), total);
        return records;
    }

    private List<Path> resolveFiles(List<Path> files, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        if (files != null && !files.isEmpty()) result.addAll(files);
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
}
