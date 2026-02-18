package com.guardtime.trace4eo.signing.commands;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.signing.OidcTokenResolver;
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
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    @Autowired
    public SigningTool(
        SigningInputValidator validator,
        RecordSigningService recordSigningService,
        RecordRegistrationClient registrationClient
    ) {
        this.validator = validator;
        this.recordSigningService = recordSigningService;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    public SigningTool(
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
        List<Path> paths = toPaths(files);
        HashAlgorithm algorithm = validator.validateHashAlgorithm(hashAlgorithm);
        validateInput(paths, provenanceRecordType, dataId);
        validator.validateFilesExist(paths);
        validator.validateOutputDirectory(outputDir);
        validator.validateRegistrationConfig(registerUrl, keycloakUrl);
        validator.validatePredecessorsFile(predecessorsFile);
        List<Predecessor> parsedPredecessors = Stream.concat(
            toPredecessors(predecessors).stream(),
            readPredecessorsFromFile(predecessorsFile).stream()
        ).toList();

        boolean hasRegistration = registerUrl != null && !registerUrl.isBlank();
        String oidcToken = oidcTokenResolver.resolve();
        String accessToken = null;
        if (hasRegistration) {
            accessToken = registrationClient.exchangeTokenIfConfigured(registerUrl, keycloakUrl, realm, oidcToken);
            registrationClient.validatePredecessorsExist(parsedPredecessors, registerUrl, accessToken);
        }

        log.info("Creating provenance record for {} file(s)...", paths.size());
        UnsignedRecord unsigned = recordSigningService.build(paths, dataId, provenanceRecordType, parsedPredecessors, algorithm);
        log.info("Signing provenance record...");
        ProvenanceRecord record = recordSigningService.sign(unsigned, oidcToken);
        recordSigningService.save(record, outputDir);
        if (hasRegistration) {
            registrationClient.registerIfConfigured(List.of(record), registerUrl, accessToken);
        }
        return record.id();
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

    private void validateInput(List<Path> files, String provenanceRecordType, String dataId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("--files must not be null or empty");
        }
        validator.validateProvenanceRecordType(provenanceRecordType);
        validator.validateDataId(dataId);
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
