package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.ProvenanceService;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2HashCheckResult;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2TraceVerificationResult;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2TraceabilityService;
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
    private final Sentinel2TraceabilityService traceabilityService;

    public Sentinel2ProvenanceController(
        ProvenanceService provenanceService,
        Sentinel2TraceabilityService traceabilityService
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
        Sentinel2TraceVerificationResult result = traceabilityService.verifyTrace(record.get().metadata().dataId());
        return ResponseEntity.ok(Sentinel2VerificationResponse.from(result));
    }

    @PostMapping("/{id}/verify-files")
    public ResponseEntity<Sentinel2HashCheckResponse> verifySentinel2Files(
        @PathVariable("id") UUID id,
        @RequestBody Sentinel2HashCheckRequest request
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
        List<Sentinel2TraceabilityService.FileHashEntry> entries = request.files().stream()
            .map(f -> new Sentinel2TraceabilityService.FileHashEntry(f.filename(), f.hashHex()))
            .toList();
        Sentinel2HashCheckResult result = traceabilityService.verifyTraceWithFileHashes(
            record.get().metadata().dataId(), entries);
        return ResponseEntity.ok(Sentinel2HashCheckResponse.from(result));
    }

    private static boolean isSentinel2(ProvenanceRecord record) {
        String dataType = record.metadata().dataType();
        return "sentinel-2".equalsIgnoreCase(dataType);
    }
}
