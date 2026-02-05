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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    private final HttpClient httpClient;
    private final ProvenanceSigningService overrideSigningService;

    public SigningTool() {
        this(HttpClient.newHttpClient());
    }

    SigningTool(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.overrideSigningService = null;
    }

    SigningTool(ProvenanceSigningService signingService, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.overrideSigningService = signingService;
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public ProvenanceRecord createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record") List<Path> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID") String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessors") List<Predecessor> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA-256") String hashAlgorithm
    ) throws IOException {
        String oidcToken = resolveOidcToken();
        ProvenanceSigningService signingService = createSigningService();
        HashAlgorithm algorithm = HashAlgorithm.valueOf(hashAlgorithm);

        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(files)
            .build();
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
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
        String oidcToken = resolveOidcToken();
        ProvenanceSigningService signingService = createSigningService();

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
                ProvenanceRecord record = createSingleFileRecord(file, dataId, provenanceRecordType, algorithm, signingService, oidcToken);
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
                String accessToken = null;
                if (keycloakUrl != null && username != null && password != null) {
                    accessToken = authenticateWithDirectGrant(keycloakUrl, realm, username, password);
                }
                registerRecords(successfulRecords, registerUrl, accessToken);
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

    private String resolveOidcToken() {
        if (overrideSigningService != null) {
            return "";
        }
        String ciToken = System.getenv("SIGSTORE_ID_TOKEN");
        if (ciToken != null && !ciToken.isBlank()) {
            log.info("Using SIGSTORE_ID_TOKEN from environment");
            return ciToken;
        }
        throw new IllegalStateException(
            "No OIDC token available. Set SIGSTORE_ID_TOKEN environment variable (e.g., from 'gcloud auth print-identity-token').");
    }

    private String authenticateWithDirectGrant(String keycloakUrl, String realm, String username, String password) {
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String body = "grant_type=password"
            + "&client_id=trace4eo-ui"
            + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
            + "&scope=openid+email";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Keycloak authentication failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            Map<String, Object> tokenResponse = new ObjectMapper().readValue(
                response.body(), new TypeReference<>() {});
            String accessToken = (String) tokenResponse.get("access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new RuntimeException("Keycloak response did not contain an access_token.");
            }
            log.info("Authenticated with Keycloak as {}", username);
            return accessToken;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to authenticate with Keycloak at " + tokenUrl, e);
        }
    }

    private ProvenanceSigningService createSigningService() {
        if (overrideSigningService != null) {
            return overrideSigningService;
        }
        return new ProvenanceSigningService();
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
        HashAlgorithm algorithm, ProvenanceSigningService signingService, String oidcToken
    ) throws IOException {
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
        ProvenanceJsonMapper provenanceJsonMapper = new ProvenanceJsonMapper();
        ZipContainerWriter writer = new ZipContainerWriter(provenanceJsonMapper);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            writer.writeTo(container, out);
        }
    }

    private void registerRecords(List<ProvenanceRecord> records, String registerUrl, String accessToken) {
        ProvenanceJsonMapper mapper = new ProvenanceJsonMapper();

        for (ProvenanceRecord record : records) {
            try {
                String json = mapper.writeValueAsString(record);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(registerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

                if (accessToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + accessToken);
                }

                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

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
}
