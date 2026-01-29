package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.graph.ProvenanceGraph;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ProvenanceControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void saveAndGetProvenanceRecord() {
        UUID id = UUID.randomUUID();
        String dataId = "data-" + id;
        ProvenanceRecord record = createTestRecord(id, dataId, Collections.emptyList());

        ResponseEntity<String> postResponse = restTemplate.postForEntity("/api/provenance", record, String.class);
        assertEquals(HttpStatus.OK, postResponse.getStatusCode(), "Save failed: " + postResponse.getBody());

        ResponseEntity<ProvenanceRecord> response = restTemplate.getForEntity(
            "/api/provenance/{id}",
            ProvenanceRecord.class,
            id
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().id());
        assertEquals(dataId, response.getBody().metadata().dataId());
    }

    @Test
    void getProvenanceRecordReturns404WhenNotFound() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ProvenanceRecord> response = restTemplate.getForEntity(
            "/api/provenance/{id}",
            ProvenanceRecord.class,
            id
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getProvenanceGraphReturnsGraphForExistingRecord() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id, "data-" + id, Collections.emptyList());
        restTemplate.postForEntity("/api/provenance", record, Void.class);

        ResponseEntity<ProvenanceGraph> response = restTemplate.getForEntity(
            "/api/provenance/{id}/graph?depth=5",
            ProvenanceGraph.class,
            id
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().rootId());
        assertEquals(1, response.getBody().nodes().size());
    }

    @Test
    void getProvenanceGraphReturns404WhenNotFound() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ProvenanceGraph> response = restTemplate.getForEntity(
            "/api/provenance/{id}/graph",
            ProvenanceGraph.class,
            id
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getProvenanceGraphWithPredecessors() {
        UUID predecessorId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();

        ProvenanceRecord predecessor = createTestRecord(predecessorId, "data-" + predecessorId, Collections.emptyList());
        ProvenanceRecord root = createTestRecord(rootId, "data-" + rootId, List.of(new Predecessor(predecessorId)));

        restTemplate.postForEntity("/api/provenance", predecessor, Void.class);
        restTemplate.postForEntity("/api/provenance", root, Void.class);

        ResponseEntity<ProvenanceGraph> response = restTemplate.getForEntity(
            "/api/provenance/{id}/graph",
            ProvenanceGraph.class,
            rootId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().nodes().size());
        assertEquals(1, response.getBody().edges().size());
    }

    @Test
    void getProvenanceGraphRejectNegativeDepth() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ProvenanceGraph> response = restTemplate.getForEntity(
            "/api/provenance/{id}/graph?depth=-1",
            ProvenanceGraph.class,
            id
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private ProvenanceRecord createTestRecord(UUID id, String dataId, List<Predecessor> predecessors) {
        Metadata metadata = new Metadata(dataId, "test-type", predecessors);
        Manifest manifest = new Manifest("1", null, null);
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        ProvenanceSignature signature = new ProvenanceSignature(
            new byte[]{1, 2, 3},
            signingTime,
            HashAlgorithm.SHA256
        );
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature);
    }
}
