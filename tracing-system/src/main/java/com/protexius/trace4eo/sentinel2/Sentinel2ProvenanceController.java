package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.ProvenanceService;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.traceability.Sentinel2DirectoryHashCheckResult;
import com.protexius.trace4eo.provenance.traceability.Sentinel2FileHashCheckResult;
import com.protexius.trace4eo.provenance.traceability.TraceVerificationResult;
import com.protexius.trace4eo.provenance.traceability.TraceabilityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ConditionalOnBean(ProvenanceService.class)
@RestController
@RequestMapping("/api/provenance/sentinel-2")
public class Sentinel2ProvenanceController {

    private final ProvenanceService provenanceService;
    private final TraceabilityService traceabilityService;

    public Sentinel2ProvenanceController(
        ProvenanceService provenanceService,
        TraceabilityService traceabilityService
    ) {
        this.provenanceService = provenanceService;
        this.traceabilityService = traceabilityService;
    }

    @PostMapping("/{id}/verify-trace")
    public ResponseEntity<Sentinel2VerificationResponse> verifySentinel2Trace(@PathVariable("id") UUID id) throws Exception {
        Optional<ProvenanceRecord> record = provenanceService.get(id);
        if (record.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isSentinel2(record.get())) {
            return ResponseEntity.badRequest().build();
        }
        TraceVerificationResult result = traceabilityService.verifyTrace(record.get().metadata().dataId());
        return ResponseEntity.ok(Sentinel2VerificationResponse.from(result));
    }

    @PostMapping("/{id}/verify-file")
    public ResponseEntity<Sentinel2FileVerificationResponse> verifySentinel2File(
        @PathVariable("id") UUID id,
        @RequestBody Sentinel2FileVerificationRequest request
    ) throws Exception {
        if (request == null || request.filename() == null || request.hashHex() == null
            || request.filename().isBlank() || request.hashHex().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ProvenanceRecord> record = provenanceService.get(id);
        if (record.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isSentinel2(record.get())) {
            return ResponseEntity.badRequest().build();
        }
        Sentinel2FileHashCheckResult result = traceabilityService.verifyTraceWithFileHash(
            record.get().metadata().dataId(), request.filename(), request.hashHex());
        return ResponseEntity.ok(Sentinel2FileVerificationResponse.from(result));
    }

    @PostMapping("/{id}/verify-files")
    public ResponseEntity<Sentinel2DirectoryVerificationResponse> verifySentinel2Files(
        @PathVariable("id") UUID id,
        @RequestBody Sentinel2DirectoryVerificationRequest request
    ) throws Exception {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ProvenanceRecord> record = provenanceService.get(id);
        if (record.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isSentinel2(record.get())) {
            return ResponseEntity.badRequest().build();
        }
        List<TraceabilityService.FileHashEntry> entries = request.files().stream()
            .map(f -> new TraceabilityService.FileHashEntry(f.filename(), f.hashHex()))
            .toList();
        Sentinel2DirectoryHashCheckResult result = traceabilityService.verifyTraceWithFileHashes(
            record.get().metadata().dataId(), entries);
        return ResponseEntity.ok(Sentinel2DirectoryVerificationResponse.from(result));
    }

    private static boolean isSentinel2(ProvenanceRecord record) {
        String dataType = record.metadata().dataType();
        return dataType != null && "sentinel-2".equalsIgnoreCase(dataType);
    }
}
