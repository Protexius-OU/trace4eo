package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.signing.OidcTokenResolver;
import com.protexius.trace4eo.signing.OutputWriter;
import com.protexius.trace4eo.signing.RecordSigningService;
import com.protexius.trace4eo.signing.UnsignedRecord;
import com.protexius.trace4eo.signing.registration.RecordRegistrationClient;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        @Option(longName = "output", description = "Output directory for saved records") Path outputDir,
        @Option(longName = "hash-algorithm", description = "Hash algorithm (SHA256, SHA384, SHA512)",
            defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm,
        @Option(longName = "create-record-ids-file",
            description = "Write a plain-text file with the IDs of all successfully signed provenance records, one UUID per line",
            defaultValue = "false") boolean createRecordIdsFile,
        @Option(longName = "save-record",
            description = "Save the provenance records",
            defaultValue = "true") boolean saveZip,
        @Option(longName = "threads",
            description = "Maximum concurrent signing threads (default: 4)",
            defaultValue = "4") int threads
    ) throws IOException, InterruptedException {
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        List<Path> resolvedFiles = validateAndResolveFiles(files, directory, pattern, provenanceRecordType,
            dataId, outputDir, registerUrl, keycloakUrl, saveZip);
        String oidcToken = resolveOidcToken();
        String accessToken = null;
        if (registerUrl != null && !registerUrl.isBlank()) {
            accessToken = registrationClient.exchangeToken(keycloakUrl, realm, oidcToken);
            registrationClient.checkSignerAccess(registerUrl, accessToken);
            registrationClient.checkUploaderAccess(registerUrl, accessToken);
        }
        List<ProvenanceRecord> records = signFilesIndividually(resolvedFiles, dataId, provenanceRecordType, algorithm,
            oidcToken, Math.max(1, threads));
        List<UUID> recordIds = records.stream().map(ProvenanceRecord::id).toList();
        if (!records.isEmpty()) {
            if (saveZip) {
                outputWriter.saveAll(records, outputDir, dataId);
            }
            registrationClient.registerIfConfigured(records, registerUrl, accessToken);
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
        String dataId, Path outputDir, String registerUrl, String keycloakUrl, boolean saveZip
    ) throws IOException {
        List<Path> filePaths = files != null ? files.stream().map(Path::of).toList() : List.of();
        validateInput(filePaths, provenanceRecordType, dataId, directory);
        validator.validateFilesExist(filePaths);
        if (saveZip) {
            validator.validateOutputDirectory(outputDir);
        }
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

    private void validateInput(
        List<Path> files, String provenanceRecordType, String dataId, Path directory
    ) {
        if ((files == null || files.isEmpty()) && directory == null) {
            throw new IllegalArgumentException("Either --files or --directory is required");
        }
        validator.validateProvenanceRecordType(provenanceRecordType);
        validator.validateDataId(dataId);
        validateDirectory(directory);
    }

    private void validateDirectory(Path directory) {
        if (directory == null) return;
        if (!Files.exists(directory)) {
            throw new IllegalArgumentException(String.format("--directory does not exist: %s", directory));
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(String.format("--directory is not a directory: %s", directory));
        }
    }

    private List<ProvenanceRecord> signFilesIndividually(
        List<Path> files, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, String oidcToken, int threads
    ) throws InterruptedException {
        int total = files.size();
        int poolSize = Math.min(threads, total);
        log.info("Signing {} file(s) with {} concurrent signer(s)...", total, poolSize);

        BlockingQueue<KeylessSigner> signerPool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            signerPool.put(recordSigningService.buildSigner(oidcToken));
        }

        AtomicInteger completedCount = new AtomicInteger(0);
        List<Future<Optional<ProvenanceRecord>>> futures = submitSigningTasks(
            files, dataId, provenanceRecordType, algorithm, signerPool, completedCount, total);

        List<ProvenanceRecord> records = collectResults(futures);
        log.info("Successfully signed {}/{} provenance records", records.size(), total);
        return records;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private List<Future<Optional<ProvenanceRecord>>> submitSigningTasks(
        List<Path> files, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, BlockingQueue<KeylessSigner> signerPool,
        AtomicInteger completedCount, int total
    ) {
        try (var progressScheduler = Executors.newSingleThreadScheduledExecutor();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            progressScheduler.scheduleAtFixedRate(
                () -> log.info("Progress: {}/{} processed...", completedCount.get(), total),
                10, 10, TimeUnit.SECONDS);
            return files.stream()
                .map(file -> executor.submit(
                    () -> signFile(file, dataId, provenanceRecordType, algorithm, signerPool, completedCount)))
                .toList();
        }
    }

    private Optional<ProvenanceRecord> signFile(
        Path file, String dataId, String provenanceRecordType,
        HashAlgorithm algorithm, BlockingQueue<KeylessSigner> signerPool,
        AtomicInteger completedCount
    ) throws InterruptedException {
        KeylessSigner signer = signerPool.take();
        try {
            String fileDataId = "%s/%s".formatted(dataId, file.getFileName());
            UnsignedRecord unsigned = recordSigningService.build(
                List.of(file), fileDataId, provenanceRecordType, List.of(), algorithm);
            return Optional.of(recordSigningService.sign(unsigned, signer));
        } catch (Exception e) {
            log.warn("Failed to create provenance record for file: {}", file, e);
            return Optional.empty();
        } finally {
            signerPool.put(signer);
            completedCount.incrementAndGet();
        }
    }

    private List<ProvenanceRecord> collectResults(List<Future<Optional<ProvenanceRecord>>> futures) {
        List<ProvenanceRecord> records = new ArrayList<>(futures.size());
        for (Future<Optional<ProvenanceRecord>> future : futures) {
            try {
                future.get().ifPresent(records::add);
            } catch (ExecutionException e) {
                log.warn("Unexpected signing task failure", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while collecting signing results", e);
            }
        }
        return records;
    }

    private List<Path> resolveFiles(List<Path> files, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        if (files != null && !files.isEmpty()) result.addAll(files);
        if (directory == null) return result;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) result.add(path);
            }
        }
        return result;
    }
}
