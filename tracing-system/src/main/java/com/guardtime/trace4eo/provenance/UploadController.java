package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.config.KeycloakBrokerTokenService;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConditionalOnBean(ProvenanceService.class)
@Profile("!test")
@RestController
@RequestMapping("/api/provenance")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final ProvenanceSigningService signingService;
    private final ProvenanceService provenanceService;
    private final KeycloakBrokerTokenService brokerTokenService;

    public UploadController(
        ProvenanceJsonMapper provenanceJsonMapper,
        ProvenanceSigningService signingService,
        ProvenanceService provenanceService,
        KeycloakBrokerTokenService brokerTokenService
    ) {
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.signingService = signingService;
        this.provenanceService = provenanceService;
        this.brokerTokenService = brokerTokenService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProvenanceRecord upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("dataType") String dataType,
        @RequestParam("dataId") String dataId,
        @RequestParam(value = "predecessors", required = false) List<String> predecessorIds
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        if (dataType == null || dataType.isBlank()) {
            throw new IllegalArgumentException("dataType must not be null or blank");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("dataId must not be null or blank");
        }
        String sanitizedFilename = file.getOriginalFilename() != null
            ? file.getOriginalFilename().replaceAll("[\\r\\n]", "_")
            : "unknown";
        log.info("Uploading file: {} with dataType: {}, dataId: {}", sanitizedFilename, dataType, dataId);

        // Extract Keycloak access token from security context
        JwtAuthenticationToken authentication =
            (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String keycloakAccessToken = authentication.getToken().getTokenValue();

        // Retrieve Sigstore ID token from Keycloak broker
        String sigstoreToken = brokerTokenService.getSigstoreIdToken(keycloakAccessToken);

        Path tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);

            List<Predecessor> predecessors = new ArrayList<>();
            if (predecessorIds != null) {
                for (String id : predecessorIds) {
                    predecessors.add(new Predecessor(UUID.fromString(id.trim())));
                }
            }

            ProvenanceRecord record = createProvenanceRecord(tempFile, dataType, dataId, predecessors, sigstoreToken);

            provenanceService.saveSignature(record);
            provenanceService.saveProvenanceRecord(record);

            log.info("Created provenance record with ID: {}", record.id());
            return record;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private ProvenanceRecord createProvenanceRecord(
        Path file,
        String dataType,
        String dataId,
        List<Predecessor> predecessors,
        String sigstoreToken
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, dataType, predecessors);

        FilesInfo filesInfo = new FilesInfoBuilder(HashAlgorithm.SHA256)
            .addFile(file)
            .build();

        Manifest manifest = new ManifestBuilder(HashAlgorithm.SHA256, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();

        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, HashAlgorithm.SHA256, sigstoreToken);

        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }
}
