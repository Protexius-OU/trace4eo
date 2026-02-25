package com.guardtime.trace4eo.signing.commands;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.signing.OidcTokenResolver;
import com.guardtime.trace4eo.signing.OutputWriter;
import com.guardtime.trace4eo.signing.RecordSigningService;
import com.guardtime.trace4eo.signing.UnsignedRecord;
import com.guardtime.trace4eo.signing.registration.RecordRegistrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Autowired
    public SigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        OutputWriter outputWriter,
        RecordRegistrationClient registrationClient
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.outputWriter = outputWriter;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    public SigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        OutputWriter outputWriter,
        RecordRegistrationClient registrationClient,
        String oidcToken
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.outputWriter = outputWriter;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(oidcToken);
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public UUID createProvenanceRecord(
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
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        List<Path> paths = validateAndResolveInput(files, provenanceRecordType, dataId, outputDir,
            registerUrl, keycloakUrl, predecessorsFile);
        List<Predecessor> parsedPredecessors = resolvePredecessors(predecessors, predecessorsFile);
        String accessToken = authenticateAndValidatePredecessors(parsedPredecessors, registerUrl, keycloakUrl, realm);
        ProvenanceRecord record = buildAndSign(paths, dataId, provenanceRecordType, parsedPredecessors, algorithm);
        saveAndRegister(record, outputDir, registerUrl, accessToken);
        return record.id();
    }

    private List<Path> validateAndResolveInput(
        List<String> files, String provenanceRecordType, String dataId, Path outputDir,
        String registerUrl, String keycloakUrl, Path predecessorsFile
    ) {
        List<Path> paths = toPaths(files);
        validateRequiredFields(paths, provenanceRecordType, dataId);
        validator.validateFilesExist(paths);
        validator.validateOutputDirectory(outputDir);
        validator.validateRegistrationConfig(registerUrl, keycloakUrl);
        validator.validatePredecessorsFile(predecessorsFile);
        return paths;
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

    private String authenticateAndValidatePredecessors(
        List<Predecessor> predecessors, String registerUrl, String keycloakUrl, String realm
    ) {
        boolean hasRegistration = registerUrl != null && !registerUrl.isBlank();
        if (!hasRegistration) return null;
        String oidcToken = oidcTokenResolver.resolve();
        String accessToken = registrationClient.exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
        if (!predecessors.isEmpty()) {
            registrationClient.validatePredecessorsExist(predecessors, registerUrl, accessToken);
        }
        return accessToken;
    }

    private ProvenanceRecord buildAndSign(
        List<Path> paths, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, HashAlgorithm algorithm
    ) throws IOException {
        log.info("Creating provenance record for {} file(s)...", paths.size());
        UnsignedRecord unsigned = recordSigningService.build(paths, dataId, provenanceRecordType, predecessors, algorithm);
        log.info("Signing provenance record...");
        String oidcToken = oidcTokenResolver.resolve();
        return recordSigningService.sign(unsigned, oidcToken);
    }

    private void saveAndRegister(
        ProvenanceRecord record, Path outputDir, String registerUrl, String accessToken
    ) throws IOException {
        outputWriter.saveRecord(record, outputDir);
        if (accessToken != null) {
            registrationClient.registerIfConfigured(List.of(record), registerUrl, accessToken);
        }
    }

    private void validateRequiredFields(List<Path> files, String provenanceRecordType, String dataId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("--files must not be null or empty");
        }
        validator.validateProvenanceRecordType(provenanceRecordType);
        validator.validateDataId(dataId);
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
}
