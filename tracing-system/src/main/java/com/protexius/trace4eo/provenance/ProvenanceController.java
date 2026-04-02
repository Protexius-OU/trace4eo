package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.graph.ProvenanceGraph;
import com.protexius.trace4eo.provenance.graph.ProvenanceGraphService;
import com.protexius.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
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

    @GetMapping
    public PagedResponse<ProvenanceRecord> listRecords(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "dataType", required = false) List<String> dataTypes,
        @RequestParam(value = "dataId", required = false) String dataId,
        @RequestParam(value = "signerIdentity", required = false) List<String> signerIdentities
    ) {
        return provenanceService.findAll(page, size, dataTypes, dataId, signerIdentities);
    }

    @GetMapping("/check-access")
    public void checkAccess() {
    }

    @GetMapping("/filters")
    public FilterOptions getFilterOptions() {
        return provenanceService.getFilterOptions();
    }

    @PostMapping("/validate-predecessors")
    public List<UUID> findMissingPredecessors(@RequestBody List<UUID> ids) {
        return provenanceService.findMissing(ids);
    }

    @PostMapping
    public void save(@RequestBody ProvenanceRecord provenanceRecord) {
        provenanceService.save(provenanceRecord);
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
        var optionalResult = provenanceGraphService.buildGraphWithRecords(id);
        if (optionalResult.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var result = optionalResult.get();
        log.info("Assembling provenance record ZIP for ID {} with {} records", id, result.records().size());

        Container container = new Container(id, new LinkedHashSet<>(result.records().values()));
        ZipContainerWriter zipContainerWriter = new ZipContainerWriter(provenanceJsonMapper);
        StreamingResponseBody stream = outputStream -> zipContainerWriter.writeTo(container, outputStream);
        return ResponseEntity.ok().body(stream);
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<ProvenanceGraph> getProvenanceGraph(@PathVariable("id") UUID id) {
        return ResponseEntity.of(provenanceGraphService.buildGraph(id));
    }

}
