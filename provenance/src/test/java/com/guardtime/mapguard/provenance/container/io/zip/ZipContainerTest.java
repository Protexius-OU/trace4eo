package com.guardtime.traceguard.provenance.container.io.zip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardtime.traceguard.provenance.container.io.ContainerWriter;
import com.guardtime.traceguard.provenance.container.io.TestUtils;
import com.guardtime.traceguard.provenance.container.model.Container;
import com.guardtime.traceguard.provenance.container.model.ProvenanceRecord;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static com.guardtime.traceguard.provenance.container.io.TestUtils.SIGNATURE_1;
import static com.guardtime.traceguard.provenance.container.io.TestUtils.SIGNATURE_2;
import static com.guardtime.traceguard.provenance.container.io.TestUtils.TEST_FILE_1;
import static com.guardtime.traceguard.provenance.container.io.TestUtils.TEST_FILE_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZipContainerTest {

    private static final String PROVENANCE_RECORD_FILENAME = "provenance-record.zip";

    @TempDir
    private static Path tempDir;

    @Test
    @Order(1)
    void write() throws IOException {
        ProvenanceRecord provenanceRecord1 = TestUtils.createProvenanceRecord(TEST_FILE_1, SIGNATURE_1);
        ProvenanceRecord provenanceRecord2 = TestUtils.createProvenanceRecord(TEST_FILE_2, SIGNATURE_2);
        List<ProvenanceRecord> records = List.of(provenanceRecord1, provenanceRecord2);
        SequencedSet<ProvenanceRecord> provenanceRecords = new LinkedHashSet<>(records);
        Container container = new Container(provenanceRecords.getLast().id(), provenanceRecords);
        ContainerWriter writer = new ZipContainerWriter();
        Path zipFilePath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        writer.writeTo(container, Files.newOutputStream(zipFilePath));
    }

    @Test
    @Order(2)
    void read() throws IOException {
        Path containerPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        ZipContainerReader reader = new ZipContainerReader(new ObjectMapper());
        Container readContainer = reader.read(containerPath);
        assertNotNull(readContainer);
        // TODO - consistent order of records in the container is nice but should not be relied upon for anything
//        assertEquals(readContainer.provenanceRecords().getLast().id(), readContainer.head());
        assertEquals(2, readContainer.provenanceRecords().size());
    }
}
