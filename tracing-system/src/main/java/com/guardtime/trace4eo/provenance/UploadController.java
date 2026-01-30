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
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ConditionalOnBean(ProvenanceService.class)
@RestController
@RequestMapping("/api/provenance")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes

    private final ProvenanceJsonMapper provenanceJsonMapper;
    private final ProvenanceSigningService signingService;
    private final ProvenanceService provenanceService;
    private final TaskExecutor taskExecutor;

    public UploadController(
        ProvenanceJsonMapper provenanceJsonMapper,
        ProvenanceSigningService signingService,
        ProvenanceService provenanceService,
        TaskExecutor taskExecutor
    ) {
        this.provenanceJsonMapper = provenanceJsonMapper;
        this.signingService = signingService;
        this.provenanceService = provenanceService;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProvenanceRecord upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("dataType") String dataType,
        @RequestParam("dataId") String dataId,
        @RequestParam(value = "predecessors", required = false) List<String> predecessorIds
    ) throws IOException {
        log.info("Uploading file: {} with dataType: {}, dataId: {}", file.getOriginalFilename(), dataType, dataId);

        Path tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);

            List<Predecessor> predecessors = new ArrayList<>();
            if (predecessorIds != null) {
                for (String id : predecessorIds) {
                    predecessors.add(new Predecessor(UUID.fromString(id.trim())));
                }
            }

            ProvenanceRecord record = createProvenanceRecord(tempFile, dataType, dataId, predecessors);

            provenanceService.saveSignature(record);
            provenanceService.saveProvenanceRecord(record);

            log.info("Created provenance record with ID: {}", record.id());
            return record;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @CrossOrigin(origins = "http://localhost:3000") // Required for dev mode (bypasses Vite proxy for SSE)
    @PostMapping(value = "/upload/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadWithSse(
        @RequestParam("file") MultipartFile file,
        @RequestParam("dataType") String dataType,
        @RequestParam("dataId") String dataId,
        @RequestParam(value = "predecessors", required = false) List<String> predecessorIds
    ) throws IOException {
        log.info("SSE upload starting: {} with dataType: {}, dataId: {}", file.getOriginalFilename(), dataType, dataId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Path tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        file.transferTo(tempFile);

        taskExecutor.execute(() -> {
            try {
                List<Predecessor> predecessors = new ArrayList<>();
                if (predecessorIds != null) {
                    for (String id : predecessorIds) {
                        predecessors.add(new Predecessor(UUID.fromString(id.trim())));
                    }
                }

                ProvenanceSigningService.OAuthUrlCallback callback = url -> {
                    try {
                        emitter.send(SseEmitter.event()
                            .name("oauth_url")
                            .data(Map.of("url", url)));
                    } catch (IOException e) {
                        log.warn("Failed to send oauth_url event", e);
                    }
                };

                ProvenanceRecord record = createProvenanceRecordWithCallback(tempFile, dataType, dataId, predecessors, callback);

                provenanceService.saveSignature(record);
                provenanceService.saveProvenanceRecord(record);

                log.info("Created provenance record with ID: {}", record.id());

                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(record));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE upload failed", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", e.getMessage() != null ? e.getMessage() : "Upload failed")));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(e);
                }
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file", e);
                }
            }
        });

        return emitter;
    }

    private ProvenanceRecord createProvenanceRecord(
        Path file,
        String dataType,
        String dataId,
        List<Predecessor> predecessors
    ) throws IOException {
        return createProvenanceRecordWithCallback(file, dataType, dataId, predecessors, null);
    }

    private ProvenanceRecord createProvenanceRecordWithCallback(
        Path file,
        String dataType,
        String dataId,
        List<Predecessor> predecessors,
        ProvenanceSigningService.OAuthUrlCallback callback
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
        ProvenanceSignature provenanceSignature = signingService.sign(manifestBytes, HashAlgorithm.SHA256, callback);

        return new ProvenanceRecordBuilder()
            .withMetadata(metadata)
            .withFilesInfo(filesInfo)
            .withManifest(manifest)
            .withSignature(provenanceSignature)
            .build();
    }
}
