package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.FileHashInfo;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.signing.SignatureDetails;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationService;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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

    public FileVerificationResponse verifyFileHashes(ProvenanceRecord record, List<FileHashInput> inputs) {
        Map<String, byte[]> resolvedHashes = resolveHashes(record, inputs);
        List<FileCheckResult> fileResults = buildFileCheckResults(record.filesInfo(), inputs);
        ProvenanceVerificationResult result = provenanceVerificationService.verifyWithFileHashes(record, resolvedHashes);
        return FileVerificationResponse.from(result, fileResults);
    }

    private Map<String, byte[]> resolveHashes(ProvenanceRecord record, List<FileHashInput> inputs) {
        Map<String, byte[]> resolved = new HashMap<>();
        for (FileHashInput input : inputs) {
            findRecordFile(record.filesInfo(), input.filename())
                .ifPresent(f -> resolved.put(f.path(), input.decodedHash()));
        }
        return resolved;
    }

    private List<FileCheckResult> buildFileCheckResults(FilesInfo filesInfo, List<FileHashInput> inputs) {
        return inputs.stream().map(input -> {
            Optional<FileHashInfo> match = findRecordFile(filesInfo, input.filename());
            if (match.isEmpty()) {
                return new FileCheckResult(input.filename(), null, FileCheckStatus.NOT_IN_RECORD);
            }
            FileHashInfo expected = match.get();
            boolean matched = Arrays.equals(input.decodedHash(), expected.hashValue());
            return new FileCheckResult(
                input.filename(),
                expected.path(),
                matched ? FileCheckStatus.MATCHED : FileCheckStatus.MISMATCH
            );
        }).toList();
    }

    private Optional<FileHashInfo> findRecordFile(FilesInfo filesInfo, String filename) {
        if (filesInfo == null || filesInfo.files() == null) {
            return Optional.empty();
        }
        return filesInfo.files().stream()
            .filter(f -> f.path() != null)
            .filter(f -> f.path().equals(filename) || f.path().endsWith("/" + filename))
            .findFirst();
    }

    public Optional<ProvenanceRecord> get(UUID id) {
        return provenanceRegistry.get(id).map(this::withManifestHash);
    }

    private ProvenanceRecord withManifestHash(ProvenanceRecord record) {
        var sig = record.signature();
        if (sig == null || sig.details() == null || sig.details().manifestHash() != null) {
            return record;
        }
        String manifestHash = computeManifestHash(record);
        var d = sig.details();
        var newDetails = new SignatureDetails(
            d.signingTime(), d.rekorLogIndex(), d.signerIdentity(), d.certificateIssuer(), manifestHash
        );
        var newSig = new ProvenanceSignature(sig.bytes(), sig.signingTime(), sig.hashAlgorithm(), newDetails);
        return new ProvenanceRecordImpl(
            record.id(), record.metadata(), record.filesInfo(), record.manifest(), newSig, record.uploaderIdentity()
        );
    }

    private String computeManifestHash(ProvenanceRecord record) {
        try {
            byte[] manifestBytes = new JsonCanonicalizer(
                provenanceJsonMapper.writeValueAsBytes(record.manifest())
            ).getEncodedUTF8();
            String algorithm = record.signature().hashAlgorithm().getName();
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(manifestBytes));
        } catch (Exception e) {
            log.warn("Failed to compute manifest hash for record {}", record.id(), e);
            return null;
        }
    }

    public List<UUID> findMissing(List<UUID> ids) {
        return provenanceRegistry.findMissing(ids);
    }

    public PagedResponse<ProvenanceRecord> findAll(int page, int size, RecordFilterCriteria criteria) {
        var records = provenanceRegistry.findAll(page, size, criteria);
        var total = provenanceRegistry.count(criteria);
        return PagedResponse.of(records, total, page, size);
    }

    public FilterOptions getFilterOptions() {
        return new FilterOptions(
            provenanceRegistry.findDistinctDataTypes(),
            provenanceRegistry.findDistinctSignerIdentities()
        );
    }

    public List<LocationCount> getLocationCounts() {
        return provenanceRegistry.countByLocation();
    }

    @Transactional
    public void save(ProvenanceRecord provenanceRecord, String uploaderIdentity) {
        validate(provenanceRecord);
        saveSignature(provenanceRecord);
        saveProvenanceRecord(provenanceRecord, uploaderIdentity);
    }

    private void validate(ProvenanceRecord record) {
        if (record.id() == null) {
            throw new IllegalArgumentException("Record ID must not be null");
        }
        if (record.metadata() == null) {
            throw new IllegalArgumentException("Metadata must not be null");
        }
        if (record.metadata().dataId() == null || record.metadata().dataId().isBlank()) {
            throw new IllegalArgumentException("dataId must not be null or blank");
        }
        if (record.metadata().dataType() == null || record.metadata().dataType().isBlank()) {
            throw new IllegalArgumentException("dataType must not be null or blank");
        }
        if (record.signature() == null) {
            throw new IllegalArgumentException("Signature must not be null");
        }
        if (record.manifest() == null) {
            throw new IllegalArgumentException("Manifest must not be null");
        }
        if (provenanceRegistry.get(record.id()).isPresent()) {
            throw new IllegalArgumentException(String.format("Record with ID %s already exists", record.id()));
        }
        validatePredecessorsExist(record);
    }

    private void validatePredecessorsExist(ProvenanceRecord provenanceRecord) {
        List<UUID> predecessorIds = provenanceRecord.metadata().predecessors() == null
            ? List.of()
            : provenanceRecord.metadata().predecessors().stream()
                .map(Predecessor::id)
                .toList();
        if (predecessorIds.isEmpty()) {
            return;
        }
        List<UUID> missing = provenanceRegistry.findMissing(predecessorIds);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(String.format("Predecessor records not found: %s", missing));
        }
    }

    public void saveSignature(ProvenanceRecord provenanceRecord) {
        UUID id = provenanceRecord.id();
        Instant signingTime = provenanceRecord.signature().signingTime();
        String signatureJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.signature());
        String signerIdentity = provenanceRecord.signature().details() != null
            ? provenanceRecord.signature().details().signerIdentity()
            : null;
        provenanceRegistry.addSignature(id, signingTime, signatureJson.getBytes(StandardCharsets.UTF_8), signerIdentity);
    }

    public void saveProvenanceRecord(ProvenanceRecord provenanceRecord, String uploaderIdentity) {
        UUID id = provenanceRecord.id();
        Instant signingTime = provenanceRecord.signature().signingTime();
        String manifestJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.manifest());
        String metadataJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.metadata());
        String filesJson = provenanceJsonMapper.writeValueAsString(provenanceRecord.filesInfo());
        provenanceRegistry.addProvenanceRecord(id, manifestJson, metadataJson, filesJson, signingTime, uploaderIdentity);
    }
}
