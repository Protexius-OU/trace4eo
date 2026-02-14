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
    public String batchSign(
        @Option(longName = "files", description = "Files to sign") List<String> files,
        @Option(longName = "directory", description = "Directory containing files to sign") Path directory,
        @Option(longName = "pattern", description = "Glob pattern for files in directory", defaultValue = "*") String pattern,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Base data ID (each file gets dataId/filename)") String dataId,
        @Option(longName = "output", description = "Output directory for ZIP file") Path outputDir,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm
    ) throws IOException {
        List<Path> filePaths = files != null ? files.stream().map(Path::of).toList() : List.of();
        HashAlgorithm algorithm = validateHashAlgorithm(hashAlgorithm);
        validateInput(filePaths, provenanceRecordType, dataId, directory);
        validateFilesExist(filePaths);
        validateOutputDirectory(outputDir);
        validateGlobPattern(pattern);
        validateRegistrationConfig(registerUrl, keycloakUrl);

        List<Path> resolvedFiles = resolveFiles(filePaths, directory, pattern);
        if (resolvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No files found matching the given --files or --directory/--pattern");
        }

        String oidcToken = oidcTokenResolver.resolve();
        SigningOutcome outcome = signFiles(resolvedFiles, dataId, provenanceRecordType, algorithm, oidcToken);

        if (!outcome.records.isEmpty()) {
            ensureDirectoryExists(outputDir);
            Path resolvedOutput = resolveOutputPath(outputDir, dataId);
            writeContainer(outcome.records, resolvedOutput);
            registerIfConfigured(outcome.records, registerUrl, keycloakUrl, realm, oidcToken);
            return formatResult(resolvedFiles.size(), outcome, resolvedOutput);
        }

        return "No records were signed successfully";
    }

    private void validateInput(
        List<Path> files, String provenanceRecordType, String dataId, Path directory
    ) {
        if ((files == null || files.isEmpty()) && directory == null) {
            throw new IllegalArgumentException("Either --files or --directory is required");
        }
        if (provenanceRecordType == null || provenanceRecordType.isBlank()) {
            throw new IllegalArgumentException("--provenance-record-type must not be null or blank");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("--data-id must not be null or blank");
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

    private void validateGlobPattern(String pattern) {
        try {
            java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid --pattern: " + pattern);
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
        List<String> failures = registrationClient.registerRecords(records, registerUrl, accessToken);
        if (!failures.isEmpty()) {
            log.warn("Registration completed with {} failure(s)", failures.size());
        }
    }

    private void ensureDirectoryExists(Path outputDir) throws IOException {
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            log.info("Created output directory: {}", outputDir.toAbsolutePath());
        }
    }

    private Path resolveOutputPath(Path outputDir, String dataId) {
        String filename = dataId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".zip";
        if (outputDir != null) {
            return outputDir.resolve(filename);
        }
        return Path.of(filename);
    }

    private String formatResult(int totalFiles, SigningOutcome outcome, Path outputPath) {
        int successCount = (int) outcome.results.stream().filter(FileSigningResult::success).count();
        int failureCount = totalFiles - successCount;
        StringBuilder sb = new StringBuilder();
        sb.append("Signed %d/%d files. Provenance records saved to %s".formatted(
            successCount, totalFiles, outputPath.toAbsolutePath()));
        if (failureCount > 0) {
            sb.append("\nFailed files:");
            for (FileSigningResult r : outcome.results) {
                if (!r.success()) {
                    sb.append("\n  ").append(r.filePath()).append(": ").append(r.errorMessage());
                }
            }
        }
        return sb.toString();
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
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, oidcToken);
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
