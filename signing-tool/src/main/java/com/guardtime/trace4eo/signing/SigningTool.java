package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public class SigningTool {

    private static final Logger log = LoggerFactory.getLogger(SigningTool.class);

    private final ProvenanceSigningService signingService;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final RecordRegistrationClient registrationClient;
    private final OidcTokenResolver oidcTokenResolver;

    @Autowired
    public SigningTool(
        ProvenanceSigningService signingService,
        ProvenanceJsonMapper provenanceJsonMapper,
        RecordRegistrationClient registrationClient
    ) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(null);
    }

    SigningTool(ProvenanceSigningService signingService, ProvenanceJsonMapper provenanceJsonMapper,
                RecordRegistrationClient registrationClient, String oidcToken) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.registrationClient = registrationClient;
        this.oidcTokenResolver = new OidcTokenResolver(oidcToken);
    }

    @Command(name = "create-provenance-record", description = "Create and sign provenance record")
    public ProvenanceRecord createProvenanceRecord(
        @Option(longName = "files", description = "Files to be included in provenance record") List<String> files,
        @Option(longName = "provenance-record-type", description = "Provenance record type") String provenanceRecordType,
        @Option(longName = "data-id", description = "Provenance record data ID") String dataId,
        @Option(longName = "predecessors", description = "Provenance record predecessors") List<Predecessor> predecessors,
        @Option(longName = "hash-algorithm", description = "Hash algorithm", defaultValue = "SHA256") String hashAlgorithm,
        @Option(longName = "register-url", description = "URL to register provenance records") String registerUrl,
        @Option(longName = "keycloak-url", description = "Keycloak server URL (for registration auth)") String keycloakUrl,
        @Option(longName = "realm", description = "Keycloak realm", defaultValue = "trace4eo") String realm
    ) throws IOException {
        List<Path> paths = toPaths(files);
        validateInput(paths, provenanceRecordType, dataId, registerUrl, keycloakUrl);

        String oidcToken = oidcTokenResolver.resolve();
        HashAlgorithm algorithm = HashAlgorithm.valueOf(hashAlgorithm);

        ProvenanceRecord record = buildSignedRecord(paths, dataId, provenanceRecordType, predecessors, algorithm, oidcToken);
        registerIfConfigured(record, registerUrl, keycloakUrl, realm, oidcToken);
        return record;
    }

    private List<Path> toPaths(List<String> files) {
        if (files == null) return List.of();
        return files.stream().map(Path::of).toList();
    }

    private void validateInput(
        List<Path> files, String provenanceRecordType, String dataId,
        String registerUrl, String keycloakUrl
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("--files must not be null or empty");
        }
        if (provenanceRecordType == null || provenanceRecordType.isBlank()) {
            throw new IllegalArgumentException("--provenance-record-type must not be null or blank");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("--data-id must not be null or blank");
        }
        if (registerUrl != null && !registerUrl.isBlank()
            && (keycloakUrl == null || keycloakUrl.isBlank())) {
            throw new IllegalArgumentException(
                "--keycloak-url is required when --register-url is provided");
        }
    }

    private void registerIfConfigured(
        ProvenanceRecord record, String registerUrl,
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
        List<String> failures = registrationClient.registerRecords(List.of(record), registerUrl, accessToken);
        if (!failures.isEmpty()) {
            log.warn("Registration failed: {}", failures.getFirst());
        }
    }

    private ProvenanceRecord buildSignedRecord(
        List<Path> files, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, HashAlgorithm algorithm, String oidcToken
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm)
            .addFiles(files)
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
}
