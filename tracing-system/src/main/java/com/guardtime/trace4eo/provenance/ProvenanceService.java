package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProvenanceService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceService.class);

    private final ProvenanceRegistry provenanceRegistry;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final ProvenanceVerificationService provenanceVerificationService;

    public ProvenanceService(
        ProvenanceRegistry provenanceRegistry,
        ProvenanceJsonMapper provenanceJsonMapper,
        ProvenanceVerificationService provenanceVerificationService
    ) {
        this.provenanceRegistry = provenanceRegistry;
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.provenanceVerificationService = provenanceVerificationService;
    }

    public ProvenanceVerificationResult verifyProvenanceRecord(ProvenanceRecord provenanceRecord) {
        Instant verificationLogCreatedAt = Instant.now();
        ProvenanceVerificationResult result = provenanceVerificationService.verify(provenanceRecord);
        provenanceRegistry.addVerificationLog(provenanceRecord.id(), verificationLogCreatedAt, result.status());
        return result;
    }

    public Optional<ProvenanceRecord> get(UUID id) {
        return provenanceRegistry.get(id);
    }

    public void saveSignature(ProvenanceRecord provenanceRecord) {
        UUID id = provenanceRecord.id();
        Instant signingTime = provenanceRecord.signature().signingTime();
        String signatureJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.signature());
        provenanceRegistry.addSignature(id, signingTime, signatureJson.getBytes(StandardCharsets.UTF_8));
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
