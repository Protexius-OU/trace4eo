package com.guardtime.trace4eo.provenance.io.zip;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import com.guardtime.trace4eo.provenance.io.ContainerReader;
import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecordBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ZipContainerReader implements ContainerReader {

    private static final Logger log = LoggerFactory.getLogger(ZipContainerReader.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;

    public ZipContainerReader(ProvenanceJsonMapper provenanceJsonMapper) {
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    @Override
    public Container read(Path path) throws IOException {
        log.debug("Reading provenance record from {}", path);
        UUID head;
        SequencedSet<ProvenanceRecord> provenanceRecords = new LinkedHashSet<>();
        try (FileSystem fileSystem = FileSystems.newFileSystem(path)) {
            head = parseHead(fileSystem.getPath(ContainerLayout.HEAD_FILE_NAME));
            Path recordsPath = fileSystem.getPath(ContainerLayout.RECORDS_DIR_NAME);
            List<Path> recordPaths;
            try (Stream<Path> pathStream = Files.list(recordsPath)) {
                recordPaths = pathStream
                    .filter(Files::isDirectory)
                    .toList();
            }
            for (Path recordPath : recordPaths) {
                // TODO - in the future should handle records with multiple manifest versions.
                //  So should try to read version first and select parser accordingly.
                provenanceRecords.add(new ProvenanceRecordParser(recordPath).parseRecord());
            }
        }
        return new Container(head, provenanceRecords);
    }

    private UUID parseHead(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            Optional<String> headString = lines.findFirst();
            if (headString.isEmpty()) {
                log.error("Head file was empty.");
                return null;
            }
            return UUID.fromString(headString.get());
        } catch (IOException e) {
            log.error("Error reading head from {}", path, e);
            // TODO exception
            throw new UncheckedIOException(e);
        }
    }

    class ProvenanceRecordParser {

        private final Path recordDir;
        private final ProvenanceRecordBuilder containerBuilder = new ProvenanceRecordBuilder();

        ProvenanceRecordParser(Path recordDir) {
            this.recordDir = recordDir;
        }

        ProvenanceRecord parseRecord() throws IOException {
            readProvenanceRecordComponent(
                    ContainerLayout.MANIFEST_FILE_NAME,
                    inputStream -> containerBuilder.withManifest(readValue(inputStream, Manifest.class))
            );
            readProvenanceRecordComponent(
                    ContainerLayout.METADATA_FILE_NAME,
                    inputStream -> containerBuilder.withMetadata(readValue(inputStream, Metadata.class))
            );
            readProvenanceRecordComponent(
                    ContainerLayout.FILES_FILE_NAME,
                    inputStream -> containerBuilder.withFilesInfo(readValue(inputStream, FilesInfo.class))
            );
            readProvenanceRecordComponent(
                    ContainerLayout.MANIFEST_SIGNATURE_FILE_NAME,
                    inputStream -> containerBuilder.withSignature(readValue(inputStream, ProvenanceSignature.class))
            );
            // TODO - check that there are no unexpected files/folders
            return containerBuilder.build();
        }

        private void readProvenanceRecordComponent(String componentName, Consumer<InputStream> consumer) throws IOException {
            Path componentPath = getComponentPathOrThrow(componentName);
            try (var is = Files.newInputStream(componentPath)) {
                consumer.accept(is);
            }
        }

        private <T> T readValue(InputStream inputStream, Class<T> klass) {
            return provenanceJsonMapper.readValue(inputStream, klass);
        }

        private Path getComponentPathOrThrow(String componentName) {
            Path componentPath = recordDir.resolve(componentName);
            if (Files.notExists(componentPath)) {
                throw new RuntimeException(String.format("Provenance record component not found: %s", componentPath));
            }
            return componentPath;
        }
    }
}
