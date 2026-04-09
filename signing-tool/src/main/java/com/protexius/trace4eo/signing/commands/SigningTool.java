package com.protexius.trace4eo.signing.commands;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.signing.OidcTokenResolver;
import com.protexius.trace4eo.signing.OutputWriter;
import com.protexius.trace4eo.signing.RecordSigningService;
import com.protexius.trace4eo.signing.UnsignedRecord;
import com.protexius.trace4eo.signing.registration.RecordRegistrationClient;
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
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    private final SigningInputValidator validator;
    private final RecordSigningService recordSigningService;
    private final OutputWriter outputWriter;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    public SigningTool(
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

    @Command(name = "get-oidc-token",
        description = "Obtain an OIDC token via browser login and print it to stdout. "
            + "Use on a machine with a browser, then copy the token to a headless environment "
            + "as SIGSTORE_ID_TOKEN.")
    public String getOidcToken() {
        String token = oidcTokenResolver.resolve();
        if (token == null) {
            throw new IllegalStateException("Failed to obtain OIDC token.");
        }
        return token;
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public UUID createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record") List<String> files,
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
            Path predecessorsFile,
        @Option(longName = "directory",
            description = "Directory containing files to be included in provenance record") Path directory,
        @Option(longName = "pattern",
            description = "Glob pattern for files in --directory",
            defaultValue = "*") String pattern,
        @Option(longName = "no-zip",
            description = "Skip writing the provenance record ZIP container to disk",
            defaultValue = "false") boolean noZip
    ) throws IOException {
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        List<Path> paths = validateAndResolveInput(files, provenanceRecordType, dataId, outputDir,
            registerUrl, keycloakUrl, predecessorsFile, directory, pattern, noZip);
        List<Predecessor> parsedPredecessors = resolvePredecessors(predecessors, predecessorsFile);
        String accessToken = exchangeToken(registerUrl, keycloakUrl, realm);
        if (!parsedPredecessors.isEmpty()) {
            registrationClient.validatePredecessorsExist(parsedPredecessors, registerUrl, accessToken);
        }
        UnsignedRecord unsigned = buildRecord(paths, dataId, provenanceRecordType, parsedPredecessors, algorithm);
        ProvenanceRecord record = sign(unsigned);
        if (!noZip) {
            outputWriter.saveRecord(record, outputDir);
        }
        registrationClient.registerIfConfigured(List.of(record), registerUrl, accessToken);
        return record.id();
    }

    private List<Path> validateAndResolveInput(
        List<String> files, String provenanceRecordType, String dataId, Path outputDir,
        String registerUrl, String keycloakUrl, Path predecessorsFile, Path directory, String pattern,
        boolean noZip
    ) throws IOException {
        validateRequiredFields(files, directory, provenanceRecordType, dataId);
        if (directory != null) {
            validator.validateInputDirectory(directory);
            validator.validateGlobPattern(pattern);
        }
        List<Path> paths = resolveFiles(files, directory, pattern);
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("No files found matching the given --files or --directory/--pattern");
        }
        validator.validateFilesExist(paths);
        if (!noZip) {
            validator.validateOutputDirectory(outputDir);
        }
        validator.validateRegistrationConfig(registerUrl, keycloakUrl);
        validator.validatePredecessorsFile(predecessorsFile);
        return paths;
    }

    private List<Path> resolveFiles(List<String> files, Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>(toPaths(files));
        if (directory != null) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        result.add(path);
                    }
                }
            }
        }
        return deduplicatePaths(result);
    }

    private List<Path> deduplicatePaths(List<Path> paths) {
        List<Path> unique = paths.stream()
            .map(Path::toAbsolutePath)
            .distinct()
            .toList();
        if (unique.size() < paths.size()) {
            log.warn("Removed {} duplicate file(s) from input", paths.size() - unique.size());
        }
        return unique;
    }

    private List<Predecessor> resolvePredecessors(List<String> predecessors, Path predecessorsFile) {
        List<Predecessor> all = Stream.concat(
            toPredecessors(predecessors).stream(),
            readPredecessorsFromFile(predecessorsFile).stream()
        ).toList();
        List<Predecessor> unique = all.stream().distinct().toList();
        if (unique.size() < all.size()) {
            log.warn("Removed {} duplicate predecessor ID(s)", all.size() - unique.size());
        }
        return unique;
    }

    private String exchangeToken(String registerUrl, String keycloakUrl, String realm) {
        if (registerUrl == null || registerUrl.isBlank()) {
            return null;
        }
        String oidcToken = oidcTokenResolver.resolve();
        String accessToken = registrationClient.exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
        if (accessToken != null) {
            registrationClient.checkSignerAccess(registerUrl, accessToken);
        }
        return accessToken;
    }

    private UnsignedRecord buildRecord(
        List<Path> paths, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, HashAlgorithm algorithm
    ) throws IOException {
        log.info("Creating provenance record for {} file(s)...", paths.size());
        return recordSigningService.build(paths, dataId, provenanceRecordType, predecessors, algorithm);
    }

    private ProvenanceRecord sign(UnsignedRecord unsigned) throws IOException {
        log.info("Signing provenance record...");
        String oidcToken = oidcTokenResolver.resolve();
        if (oidcToken == null) {
            throw new IllegalStateException(
                "No OIDC token available. Set SIGSTORE_ID_TOKEN or enable browser-based login.");
        }
        return recordSigningService.sign(unsigned, oidcToken);
    }

    private void validateRequiredFields(
        List<String> files, Path directory, String provenanceRecordType, String dataId
    ) {
        if ((files == null || files.isEmpty()) && directory == null) {
            throw new IllegalArgumentException("At least one of --files or --directory must be provided");
        }
        validator.validateProvenanceRecordType(provenanceRecordType);
        validator.validateDataId(dataId);
    }

    private List<Path> toPaths(List<String> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(Path::of).toList();
    }

    private List<Predecessor> toPredecessors(List<String> predecessors) {
        if (predecessors == null) {
            return List.of();
        }
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

    private List<Predecessor> readPredecessorsFromFile(Path predecessorsFile) {
        if (predecessorsFile == null) {
            return List.of();
        }
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
}
