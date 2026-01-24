package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.graph.ProvenanceGraph;
import com.guardtime.trace4eo.provenance.graph.ProvenanceGraphService;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ConditionalOnBean(ProvenanceService.class)
@RestController
@RequestMapping("/api/provenance")
public class ProvenanceController {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceController.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final ProvenanceService provenanceService;
    private final ProvenanceGraphService provenanceGraphService;

    public ProvenanceController(
        ProvenanceJsonMapper provenanceJsonMapper,
        ProvenanceService provenanceService,
        ProvenanceGraphService provenanceGraphService
    ) {
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.provenanceService = provenanceService;
        this.provenanceGraphService = provenanceGraphService;
    }

    @PostMapping
    public void save(@RequestBody ProvenanceRecord provenanceRecord) {
        provenanceService.saveSignature(provenanceRecord);
        provenanceService.saveProvenanceRecord(provenanceRecord);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProvenanceRecord> getProvenanceRecord(@PathVariable("id") UUID id) {
        Optional<ProvenanceRecord> provenanceRecord = provenanceService.get(id);
        return ResponseEntity.of(provenanceRecord);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ProvenanceVerificationResult> verifyProvenanceRecord(@PathVariable("id") UUID id) {
        Optional<ProvenanceRecord> provenanceRecord = provenanceService.get(id);
        if (provenanceRecord.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ProvenanceVerificationResult result = provenanceService.verifyProvenanceRecord(provenanceRecord.get());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/zip")
    public ResponseEntity<StreamingResponseBody> downloadProvenanceRecordZip(@PathVariable("id") UUID id) {
        Optional<ProvenanceRecord> optionalProvenanceRecord = provenanceService.get(id);
        if (optionalProvenanceRecord.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.info("Assembling provenance record for ID {}", id);
        List<ProvenanceRecord> provenanceRecords = new ArrayList<>();
        ProvenanceRecord provenanceRecord = optionalProvenanceRecord.get();
        List<Predecessor> predecessors = provenanceRecord.metadata().predecessors();
        if (predecessors != null && !predecessors.isEmpty()) {
            log.info("Found {} predecessors for provenance record.", predecessors.size());
            for (Predecessor predecessor : predecessors) {
                UUID predecessorId = predecessor.id();
                Optional<ProvenanceRecord> optionalPredecessor = provenanceService.get(predecessorId);
                if (optionalPredecessor.isEmpty()) {
                    log.error("Mosaic {} referenced a predecessor by ID {} that was not found.", id, predecessorId);
                    return ResponseEntity.internalServerError().build();
                }
                provenanceRecords.add(optionalPredecessor.get());
            }
        }
        provenanceRecords.add(provenanceRecord);
        ZipContainerWriter zipContainerWriter = new ZipContainerWriter(provenanceJsonMapper);
        Container container = new Container(provenanceRecord.id(), new LinkedHashSet<>(provenanceRecords));
        log.info("Starting to send assembled provenance record with ID {} back to user.", id);
        StreamingResponseBody stream = outputStream -> zipContainerWriter.writeTo(container, outputStream);
        return ResponseEntity.ok().body(stream);
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<ProvenanceGraph> getProvenanceGraph(
        @PathVariable("id") UUID id,
        @RequestParam(value = "depth", defaultValue = "10") int depth
    ) {
        if (depth < 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(provenanceGraphService.buildGraph(id, depth));
    }

    @GetMapping("/{id}/graph/viewer")
    public RedirectView viewProvenanceGraph(@PathVariable("id") UUID id) {
        return new RedirectView("/graph-viewer.html?id=" + id);
    }
}
