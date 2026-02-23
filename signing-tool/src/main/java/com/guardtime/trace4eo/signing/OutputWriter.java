package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.io.zip.ZipContainerWriter;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
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
import java.util.UUID;

@Component
public class OutputWriter {

    private static final Logger log = LoggerFactory.getLogger(OutputWriter.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;

    public OutputWriter(ProvenanceJsonMapper provenanceJsonMapper) {
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    public void saveRecord(ProvenanceRecord record, Path outputDir) throws IOException {
        prepareOutputDirectory(outputDir);
        Path outputPath = resolveOutputPath(outputDir, record.id() + ".zip");
        Container container = new Container(record.id(), new LinkedHashSet<>(Set.of(record)));
        writeContainer(container, outputPath);
        log.info("Provenance record saved to {}", outputPath.toAbsolutePath());
    }

    public void saveAll(List<ProvenanceRecord> records, Path outputDir, String dataId) throws IOException {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("records must not be empty");
        }
        prepareOutputDirectory(outputDir);
        String filename = sanitizeBaseName(dataId) + ".zip";
        Path outputPath = resolveOutputPath(outputDir, filename);
        ProvenanceRecord headRecord = records.getLast();
        Container container = new Container(headRecord.id(), new LinkedHashSet<>(records));
        writeContainer(container, outputPath);
        log.info("Provenance records saved to {}", outputPath.toAbsolutePath());
    }

    public void writeRecordIds(List<UUID> recordIds, Path outputDir, String dataId) throws IOException {
        prepareOutputDirectory(outputDir);
        String filename = sanitizeBaseName(dataId) + "-record-ids.txt";
        Path recordIdsPath = resolveOutputPath(outputDir, filename);
        List<String> ids = recordIds.stream().map(UUID::toString).toList();
        Files.writeString(recordIdsPath, String.join("\n", ids) + "\n");
        log.info("Record IDs written to: {}", recordIdsPath);
    }

    private static String sanitizeBaseName(String dataId) {
        return dataId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void prepareOutputDirectory(Path outputDir) throws IOException {
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
