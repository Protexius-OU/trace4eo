package com.guardtime.trace4eo.provenance.container.io.json;

import com.guardtime.trace4eo.provenance.container.io.ContainerWriter;
import com.guardtime.trace4eo.provenance.container.io.TestUtils;
import com.guardtime.trace4eo.provenance.container.model.Container;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.container.model.ProvenanceRecord;
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

import static com.guardtime.trace4eo.provenance.container.io.TestUtils.SIGNATURE_1;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.SIGNATURE_2;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.TEST_FILE_1;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.TEST_FILE_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JsonContainerTest {

    private static final String PROVENANCE_RECORD_FILENAME = "provenance-record.json";

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
        ContainerWriter writer = new JsonContainerWriter(new ProvenanceJsonMapper());
        Path provenanceRecordPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        writer.writeTo(container, Files.newOutputStream(provenanceRecordPath));
    }

    @Test
    @Order(2)
    void read() throws IOException {
        JsonContainerReader reader = new JsonContainerReader(new ProvenanceJsonMapper());
        Path provenanceRecordPath = tempDir.resolve(PROVENANCE_RECORD_FILENAME);
        Container readContainer = reader.read(provenanceRecordPath);
        assertNotNull(readContainer);
        assertEquals(readContainer.provenanceRecords().getLast().id(), readContainer.head());
        assertEquals(2, readContainer.provenanceRecords().size());
    }
}
