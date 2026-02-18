package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.FilesInfoBuilder;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.ManifestBuilder;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import dev.sigstore.KeylessSigner;
import dev.sigstore.json.canonicalizer.JsonCanonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RecordSigningService {

    private static final Logger log = LoggerFactory.getLogger(RecordSigningService.class);

    private final ProvenanceSigningService signingService;
    private final ProvenanceJsonMapper provenanceJsonMapper;

    public RecordSigningService(ProvenanceSigningService signingService, ProvenanceJsonMapper provenanceJsonMapper) {
        this.signingService = signingService;
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    public UnsignedRecord build(
        List<Path> files, String dataId, String provenanceRecordType,
        List<Predecessor> predecessors, HashAlgorithm algorithm
    ) throws IOException {
        Metadata metadata = new Metadata(dataId, provenanceRecordType, predecessors);
        FilesInfo filesInfo = new FilesInfoBuilder(algorithm).addFiles(files).build();
        Manifest manifest = new ManifestBuilder(algorithm, provenanceJsonMapper)
            .withFilesInfo(filesInfo)
            .withMetadata(metadata)
            .build();
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(manifest)).getEncodedUTF8();
        return new UnsignedRecord(metadata, filesInfo, manifest, manifestBytes);
    }

    public ProvenanceRecord sign(UnsignedRecord unsigned, String oidcToken) throws IOException {
        ProvenanceSignature signature = signingService.sign(unsigned.manifestBytes(), oidcToken);
        return assembleRecord(unsigned, signature);
    }

    public ProvenanceRecord sign(UnsignedRecord unsigned, KeylessSigner signer) throws IOException {
        ProvenanceSignature signature = signingService.sign(unsigned.manifestBytes(), signer);
        return assembleRecord(unsigned, signature);
    }

    public Path save(ProvenanceRecord record, Path outputDir) throws IOException {
        ensureDirectoryExists(outputDir);
        Path outputPath = resolveOutputPath(outputDir, record.id() + ".zip");
        Container container = new Container(record.id(), new LinkedHashSet<>(Set.of(record)));
        writeContainer(container, outputPath);
        log.info("Provenance record saved to {}", outputPath.toAbsolutePath());
        return outputPath;
    }

    public KeylessSigner buildSigner(String oidcToken) {
        return signingService.buildTokenSigner(oidcToken);
    }

    public Path saveAll(List<ProvenanceRecord> records, Path outputDir, String dataId) throws IOException {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("records must not be empty");
        }
        ensureDirectoryExists(outputDir);
        String filename = dataId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".zip";
        Path outputPath = resolveOutputPath(outputDir, filename);
        ProvenanceRecord headRecord = records.getLast();
        Container container = new Container(headRecord.id(), new LinkedHashSet<>(records));
        writeContainer(container, outputPath);
        log.info("Provenance records saved to {}", outputPath.toAbsolutePath());
        return outputPath;
    }

    private ProvenanceRecord assembleRecord(UnsignedRecord unsigned, ProvenanceSignature signature) throws IOException {
        return new ProvenanceRecordBuilder()
            .withMetadata(unsigned.metadata())
            .withFilesInfo(unsigned.filesInfo())
            .withManifest(unsigned.manifest())
            .withSignature(signature)
            .build();
    }

    private void ensureDirectoryExists(Path outputDir) throws IOException {
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            log.info("Created output directory: {}", outputDir.toAbsolutePath());
        }
    }

    private Path resolveOutputPath(Path outputDir, String filename) {
        if (outputDir != null) return outputDir.resolve(filename);
        return Path.of(filename);
    }

    private void writeContainer(Container container, Path outputPath) throws IOException {
        ZipContainerWriter writer = new ZipContainerWriter(provenanceJsonMapper);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            writer.writeTo(container, out);
        }
    }
}
