package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProvenanceService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceService.class);

    private final ProvenanceRegistry provenanceRegistry;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final ProvenanceSigningService provenanceSigningService;
    private final ProvenanceVerificationService provenanceVerificationService;

    public ProvenanceService(
        ProvenanceRegistry provenanceRegistry,
        ProvenanceJsonMapper provenanceJsonMapper,
        ProvenanceSigningService provenanceSigningService,
        ProvenanceVerificationService provenanceVerificationService
    ) {
        this.provenanceRegistry = provenanceRegistry;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.provenanceSigningService = provenanceSigningService;
        this.provenanceVerificationService = provenanceVerificationService;
    }

    public ProvenanceVerificationResult verifyProvenanceRecord(ProvenanceRecord provenanceRecord) {
        Instant verificationLogCreatedAt = Instant.now();
        ProvenanceVerificationResult result = provenanceVerificationService.verify(provenanceRecord);
        provenanceRegistry.addVerificationLog(provenanceRecord.id(), verificationLogCreatedAt, result.status());
        return result;
    }

    @Transactional
    public ProvenanceRecord createProvenanceRecord(
        HashAlgorithm hashAlgorithm,
        List<Path> paths,
        String provenanceRecordType,
        String dataId,
        List<Predecessor> predecessors
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(hashAlgorithm)
            .addFiles(paths)
            .build();
        Manifest manifest = new ManifestBuilder(hashAlgorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = provenanceJsonMapper.writeValueAsBytes(manifest);
        ProvenanceSignature signature = provenanceSigningService.sign(manifestBytes, hashAlgorithm);
        ProvenanceRecord provenanceRecord = new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(signature)
            .build();
        log.info("Storing provenance record with ID {} for data ID {}", provenanceRecord.id(), dataId);
        saveSignature(provenanceRecord);
        saveProvenanceRecord(provenanceRecord);
        return provenanceRecord;
    }

    public Optional<ProvenanceRecord> get(UUID id) {
        return provenanceRegistry.get(id);
    }

    public void saveSignature(ProvenanceRecord provenanceRecord) {
        UUID id = provenanceRecord.id();
        Instant signingTime = provenanceRecord.signature().signingTime();
        byte[] signatureBytes = provenanceRecord.signature().bytes();
        provenanceRegistry.addSignature(id, signingTime, signatureBytes);
    }

    public void saveProvenanceRecord(ProvenanceRecord provenanceRecord) {
        UUID id = provenanceRecord.id();
        Instant signingTime = provenanceRecord.signature().signingTime();
        String manifestJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.manifest());
        String metadataJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.metadata());
        String filesJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.filesInfo());
        provenanceRegistry.addProvenanceRecord(id, manifestJson, metadataJson, filesJson, signingTime);
    }
}
