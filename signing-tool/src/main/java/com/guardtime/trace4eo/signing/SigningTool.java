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
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    private final ProvenanceSigningService signingService;
    private final HttpClient httpClient;

    public SigningTool() {
        this(new ProvenanceSigningService(), HttpClient.newHttpClient());
    }

    SigningTool(ProvenanceSigningService signingService, HttpClient httpClient) {
        this.signingService = signingService;
        this.httpClient = httpClient;
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public ProvenanceRecord createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record") List<Path> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID") String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessors") List<Predecessor> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA-256") String hashAlgorithm
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.valueOf(hashAlgorithm))
            .addFiles(files)
            .build();
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        Manifest manifest = new ManifestBuilder(HashAlgorithm.valueOf(hashAlgorithm), provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, HashAlgorithm.valueOf(hashAlgorithm));
        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
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
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl
    ) throws IOException {
        List<Path> resolvedFiles = resolveFiles(files, directory, pattern);

        if (resolvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No files to sign. Provide --files or --directory with --pattern");
        }
        if (resolvedFiles.size() > 100) {
            throw new IllegalArgumentException("Cannot sign more than 100 files at once. Got: " + resolvedFiles.size());
        }

        HashAlgorithm algorithm = HashAlgorithm.valueOf(hashAlgorithm);
        List<FileSigningResult> results = new ArrayList<>();
        List<ProvenanceRecord> successfulRecords = new ArrayList<>();

        for (Path file : resolvedFiles) {
            try {
                ProvenanceRecord record = createSingleFileRecord(file, dataId, provenanceRecordType, algorithm);
                successfulRecords.add(record);
                results.add(FileSigningResult.success(file, record.id()));
                log.info("Successfully signed: {}", file);
            } catch (Exception e) {
                log.warn("Failed to sign file: {}", file, e);
                results.add(FileSigningResult.failure(file, e.getMessage()));
            }
        }

        if (!successfulRecords.isEmpty()) {
            writeContainer(successfulRecords, outputPath);
            log.info("Written {} records to {}", successfulRecords.size(), outputPath);

            if (registerUrl != null && !registerUrl.isBlank()) {
                registerRecords(successfulRecords, registerUrl);
            }
        }

        int successCount = (int) results.stream().filter(FileSigningResult::success).count();
        return new BatchSigningResult(
            resolvedFiles.size(),
            successCount,
            resolvedFiles.size() - successCount,
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

    private ProvenanceRecord createSingleFileRecord(Path file, String baseDataId, String provenanceRecordType, HashAlgorithm algorithm) throws IOException {
        String fileDataId = baseDataId + "/" + file.getFileName().toString();
        Metadata metadata = new Metadata(fileDataId, provenanceRecordType, List.of());
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(List.of(file))
            .build();
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, algorithm);
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
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        ZipContainerWriter writer = new ZipContainerWriter(provenanceJsonMapper);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            writer.writeTo(container, out);
        }
    }

    private void registerRecords(List<ProvenanceRecord> records, String registerUrl) {
        ProvenanceJsonMapper mapper = new ProvenanceJsonMapper();

        for (ProvenanceRecord record : records) {
            try {
                String json = mapper.writeValueAsString(record);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Registered record {} at {}", record.id(), registerUrl);
                } else {
                    log.warn("Failed to register record {}: HTTP {} - {}", record.id(), response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Failed to register record {}: {}", record.id(), e.getMessage(), e);
            }
        }
    }

    private byte[] resolveInput(Path file, String hex, String base64) {
        if (file != null) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                log.warn("Failed to read file {}", file, e);
                throw new RuntimeException(e);
            }
        }
        if (hex != null) {
            return HexFormat.of().parseHex(hex);
        }
        if (base64 != null) {
            return Base64.getDecoder().decode(base64);
        }
        throw new IllegalArgumentException("Input data was missing");
    }
}
