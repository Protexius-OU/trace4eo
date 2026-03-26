package com.protexius.trace4eo.provenance.io.zip;

import com.protexius.trace4eo.provenance.Container;
import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.io.ContainerWriter;
import com.protexius.trace4eo.provenance.io.TestUtils;
import com.protexius.trace4eo.provenance.record.FileHashInfo;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationResult;
import com.protexius.trace4eo.provenance.verification.ProvenanceVerificationService;
import com.protexius.trace4eo.provenance.verification.VerificationStep;
import com.protexius.trace4eo.provenance.verification.VerificationStepName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.protexius.trace4eo.provenance.io.TestUtils.TEST_FILE_1;
import static com.protexius.trace4eo.provenance.io.TestUtils.TEST_FILE_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZipContainerTest {

    private static final String PROVENANCE_RECORD_FILENAME = "provenance-record.zip";

    @TempDir
    private static Path tempDir;

    @Test
    @Order(1)
    void write() throws IOException {
        ProvenanceRecord provenanceRecord1 = TestUtils.createProvenanceRecord(TEST_FILE_1);
        ProvenanceRecord provenanceRecord2 = TestUtils.createProvenanceRecord(TEST_FILE_2);
        List<ProvenanceRecord> records = List.of(provenanceRecord1, provenanceRecord2);
        SequencedSet<ProvenanceRecord> provenanceRecords = new LinkedHashSet<>(records);
        Container container = new Container(provenanceRecords.getLast().id(), provenanceRecords);
        ContainerWriter writer = new ZipContainerWriter(new ProvenanceJsonMapper());
        Path zipFilePath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        writer.writeTo(container, Files.newOutputStream(zipFilePath));
    }

    @Test
    @Order(2)
    void read() throws IOException {
        Path containerPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        ZipContainerReader reader = new ZipContainerReader(new ProvenanceJsonMapper());
        Container readContainer = reader.read(containerPath);
        assertNotNull(readContainer);
        assertEquals(2, readContainer.provenanceRecords().size());
    }

    @Test
    @Order(3)
    void readSetsFilesContextForRecordsWithFiles() throws IOException {
        Path containerPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        ZipContainerReader reader = new ZipContainerReader(new ProvenanceJsonMapper());
        Container readContainer = reader.read(containerPath);
        for (ProvenanceRecord record : readContainer.provenanceRecords()) {
            assertNotNull(record.filesInfo().filesContext(),
                "FilesContext should be set when files are present in container");
        }
    }

    @Test
    @Order(4)
    void fileContentVerificationPassesForUnmodifiedContainer() throws IOException {
        Path containerPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        ZipContainerReader reader = new ZipContainerReader(new ProvenanceJsonMapper());
        Container readContainer = reader.read(containerPath);
        ProvenanceVerificationService service = new ProvenanceVerificationService();
        for (ProvenanceRecord record : readContainer.provenanceRecords()) {
            ProvenanceVerificationResult result = service.verify(record);
            VerificationStep fileContentsStep = result.steps().stream()
                .filter(s -> s.name() == VerificationStepName.FILE_CONTENTS)
                .findFirst().orElseThrow();
            assertTrue(fileContentsStep.status(),
                "FILE_CONTENTS step should pass for unmodified container: " + fileContentsStep.errorMessage());
            assertFalse(fileContentsStep.description().contains("skipped"),
                "FILE_CONTENTS step should not be skipped");
        }
    }

    @Test
    void renamedFileInContainerCausesVerificationFailure() throws IOException {
        ProvenanceRecord record = TestUtils.createProvenanceRecord(TEST_FILE_1);
        Container container = new Container(record.id(), new LinkedHashSet<>(List.of(record)));
        Path zipPath = tempDir.resolve("renamed-file-test.zip");
        new ZipContainerWriter(new ProvenanceJsonMapper()).writeTo(container, Files.newOutputStream(zipPath));

        FileHashInfo fileHashInfo = record.filesInfo().files().iterator().next();
        String originalName = fileHashInfo.path();
        String renamedName = "renamed_" + originalName;

        Path modifiedZipPath = tempDir.resolve("renamed-file-modified.zip");
        try (FileSystem srcFs = FileSystems.newFileSystem(zipPath);
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(modifiedZipPath));
             Stream<Path> entries = Files.walk(srcFs.getPath("/"))) {
            entries.filter(Files::isRegularFile).forEach(srcPath -> {
                String entryName = srcPath.toString().substring(1); // strip leading /
                String destName = entryName.endsWith("/" + originalName)
                    ? entryName.substring(0, entryName.length() - originalName.length()) + renamedName
                    : entryName;
                try {
                    zos.putNextEntry(new ZipEntry(destName));
                    Files.copy(srcPath, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        ZipContainerReader reader = new ZipContainerReader(new ProvenanceJsonMapper());
        Container readContainer = reader.read(modifiedZipPath);
        ProvenanceVerificationService service = new ProvenanceVerificationService();
        ProvenanceRecord readRecord = readContainer.provenanceRecords().iterator().next();
        ProvenanceVerificationResult result = service.verify(readRecord);

        assertFalse(result.status(), "Verification should fail when a file is renamed in the container");
        VerificationStep fileContentsStep = result.steps().stream()
            .filter(s -> s.name() == VerificationStepName.FILE_CONTENTS)
            .findFirst().orElseThrow();
        assertFalse(fileContentsStep.status(), "FILE_CONTENTS step should fail when file is renamed");
        assertNotNull(fileContentsStep.errorMessage());
        assertTrue(fileContentsStep.errorMessage().contains(originalName),
            "Error message should mention the expected filename: " + fileContentsStep.errorMessage());
    }
}
