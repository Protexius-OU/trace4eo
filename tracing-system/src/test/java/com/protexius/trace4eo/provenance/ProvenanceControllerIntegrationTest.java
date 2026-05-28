package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.graph.ProvenanceGraph;
import com.protexius.trace4eo.provenance.record.FileHashInfo;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import com.protexius.trace4eo.provenance.signing.SignatureDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
class ProvenanceControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProvenanceService provenanceService;

    @Test
    void checkAccessReturns200() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/provenance/check-access", Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void checkUploaderAccessReturns200() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/provenance/check-uploader-access", Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void saveAndGetProvenanceRecord() {
        UUID id = UUID.randomUUID();
        String dataId = "data-" + id;
        ProvenanceRecord record = createTestRecord(id, dataId, "test-type", null, Collections.emptyList());

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
        ProvenanceRecord record = createTestRecord(id, "data-" + id, "test-type", null, Collections.emptyList());
        restTemplate.postForEntity("/api/provenance", record, Void.class);

        ResponseEntity<ProvenanceGraph> response = restTemplate.getForEntity(
            "/api/provenance/{id}/graph",
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

        ProvenanceRecord predecessor = createTestRecord(predecessorId, "data-" + predecessorId, "test-type", null, Collections.emptyList());
        ProvenanceRecord root = createTestRecord(rootId, "data-" + rootId, "test-type", null, List.of(new Predecessor(predecessorId)));

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
    void getFilterOptionsReturnsDistinctValues() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String typeA = "filter-type-a-" + uniqueSuffix;
        String typeB = "filter-type-b-" + uniqueSuffix;
        String signer = "filtertest-" + uniqueSuffix + "@example.com";

        createAndSaveRecord(typeA, signer);
        createAndSaveRecord(typeB, signer);
        createAndSaveRecord(typeA, signer);

        ResponseEntity<FilterOptions> response = restTemplate.getForEntity(
            "/api/provenance/filters",
            FilterOptions.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().dataTypes().contains(typeA));
        assertTrue(response.getBody().dataTypes().contains(typeB));
        assertTrue(response.getBody().signerIdentities().contains(signer));
    }

    @Test
    void listRecordsWithMultiValueDataTypeFilter() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String typeA = "mv-type-a-" + uniqueSuffix;
        String typeB = "mv-type-b-" + uniqueSuffix;
        String typeC = "mv-type-c-" + uniqueSuffix;

        createAndSaveRecord(typeA, null);
        createAndSaveRecord(typeB, null);
        createAndSaveRecord(typeC, null);

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance?dataType={t1}&dataType={t2}",
            String.class,
            typeA, typeB
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(typeA));
        assertTrue(response.getBody().contains(typeB));
        // typeC should not appear
        assertTrue(!response.getBody().contains(typeC));
    }

    @Test
    void listRecordsWithInvalidAttributeFilterReturns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance?attribute=broken",
            String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("broken"));
    }

    @Test
    void listRecordsWithAttributeFilter() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String dataType = "attr-type-" + uniqueSuffix;
        String location = "loc-" + uniqueSuffix;
        createAndSaveRecordWithAttributes(dataType, null, Map.of("location", location));
        createAndSaveRecordWithAttributes(dataType, null, Map.of("location", "other-" + uniqueSuffix));

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance?dataType={t}&attribute=location={l}",
            String.class,
            dataType, location
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"totalElements\":1"), response.getBody());
        assertTrue(response.getBody().contains(location));
    }

    @Test
    void listRecordsWithSignerIdentityFilter() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String signer1 = "signer1-" + uniqueSuffix + "@example.com";
        String signer2 = "signer2-" + uniqueSuffix + "@example.com";

        createAndSaveRecord("type-" + uniqueSuffix, signer1);
        createAndSaveRecord("type-" + uniqueSuffix, signer2);

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance?signerIdentity={s}",
            String.class,
            signer1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Response should contain exactly 1 record (total)
        assertTrue(response.getBody().contains("\"totalElements\":1"));
    }

    @Test
    void saveWithDuplicateIdReturns400() {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id, "data-" + id, "test-type", null, Collections.emptyList());

        restTemplate.postForEntity("/api/provenance", record, String.class);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/provenance", record, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains(id.toString()));
    }

    @Test
    void validatePredecessorsReturnsEmptyListWhenAllExist() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProvenanceRecord record1 = createTestRecord(id1, "data-" + id1, "test-type", null, Collections.emptyList());
        ProvenanceRecord record2 = createTestRecord(id2, "data-" + id2, "test-type", null, Collections.emptyList());
        restTemplate.postForEntity("/api/provenance", record1, Void.class);
        restTemplate.postForEntity("/api/provenance", record2, Void.class);

        ResponseEntity<UUID[]> response = restTemplate.postForEntity(
            "/api/provenance/validate-predecessors",
            List.of(id1, id2),
            UUID[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void validatePredecessorsReturnsMissingIds() {
        UUID existingId = UUID.randomUUID();
        UUID missingId1 = UUID.randomUUID();
        UUID missingId2 = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(existingId, "data-" + existingId, "test-type", null, Collections.emptyList());
        restTemplate.postForEntity("/api/provenance", record, Void.class);

        ResponseEntity<UUID[]> response = restTemplate.postForEntity(
            "/api/provenance/validate-predecessors",
            List.of(existingId, missingId1, missingId2),
            UUID[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);
        List<UUID> missing = List.of(response.getBody());
        assertTrue(missing.contains(missingId1));
        assertTrue(missing.contains(missingId2));
    }

    @Test
    void validatePredecessorsReturnsAllWhenNoneExist() {
        UUID missingId = UUID.randomUUID();

        ResponseEntity<UUID[]> response = restTemplate.postForEntity(
            "/api/provenance/validate-predecessors",
            List.of(missingId),
            UUID[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().length);
        assertEquals(missingId, response.getBody()[0]);
    }

    @Test
    void saveWithNonExistingPredecessorReturns400() {
        UUID id = UUID.randomUUID();
        UUID nonExistingPredecessorId = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(
            id, "data-" + id, "test-type", null, List.of(new Predecessor(nonExistingPredecessorId))
        );

        ResponseEntity<String> response = restTemplate.postForEntity("/api/provenance", record, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains(nonExistingPredecessorId.toString()));
    }

    @Test
    void verifyFileHashesEndpointMatchesCorrectHash() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] fileBytes = {42, 84, 126};
        byte[] fileHash = MessageDigest.getInstance("SHA-256").digest(fileBytes);
        String hashBase64 = Base64.getEncoder().encodeToString(fileHash);

        FileHashInfo fileHashInfo = new FileHashInfo("test.dat", HashAlgorithm.SHA256, fileHash);
        FileHashInfo dummyHash = new FileHashInfo(HashAlgorithm.SHA256, new byte[32]);
        FilesInfo filesInfo = new FilesInfo(new LinkedHashSet<>(List.of(fileHashInfo)), null);
        Manifest manifest = new Manifest(dummyHash, dummyHash);
        Metadata metadata = new Metadata("data-" + id, "test-type", Collections.emptyList(), null);
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1, 2, 3}, signingTime, HashAlgorithm.SHA256);
        ProvenanceRecord record = new ProvenanceRecordImpl(id, metadata, filesInfo, manifest, signature, null);
        provenanceService.save(record, null);

        List<FileHashInput> inputs = List.of(new FileHashInput("test.dat", hashBase64));
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/provenance/{id}/verify-files", inputs, String.class, id
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"MATCHED\""));
    }

    @Test
    void getChainLocationCountsAggregatesAcrossPredecessors() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String type = "chain-loc-" + uniqueSuffix;
        String oslo = "oslo-" + uniqueSuffix;
        String bergen = "bergen-" + uniqueSuffix;
        String tromso = "tromso-" + uniqueSuffix;

        UUID predA = UUID.randomUUID();
        UUID predB = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        restTemplate.postForEntity("/api/provenance",
            createTestRecord(predA, "data-" + predA, type, null, Collections.emptyList(), Map.of("location", oslo)),
            Void.class);
        restTemplate.postForEntity("/api/provenance",
            createTestRecord(predB, "data-" + predB, type, null, Collections.emptyList(), Map.of("location", oslo)),
            Void.class);
        restTemplate.postForEntity("/api/provenance",
            createTestRecord(root, "data-" + root, type, null,
                List.of(new Predecessor(predA), new Predecessor(predB)), Map.of("location", bergen)),
            Void.class);
        restTemplate.postForEntity("/api/provenance",
            createTestRecord(unrelated, "data-" + unrelated, type, null,
                Collections.emptyList(), Map.of("location", tromso)),
            Void.class);

        ResponseEntity<LocationCount[]> response = restTemplate.getForEntity(
            "/api/provenance/{id}/location-counts", LocationCount[].class, root
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        List<LocationCount> counts = List.of(response.getBody());
        assertEquals(2, counts.size());
        assertEquals(oslo, counts.get(0).country());
        assertEquals(2, counts.get(0).recordCount());
        assertEquals(bergen, counts.get(1).country());
        assertEquals(1, counts.get(1).recordCount());
    }

    @Test
    void getChainLocationCountsReturns404WhenRecordMissing() {
        UUID unknown = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance/{id}/location-counts", String.class, unknown
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listRecordsScopedToChainWithInChainOf() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String type = "in-chain-" + uniqueSuffix;
        UUID pred = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        restTemplate.postForEntity("/api/provenance",
            createTestRecord(pred, "data-" + pred, type, null, Collections.emptyList()), Void.class);
        restTemplate.postForEntity("/api/provenance",
            createTestRecord(root, "data-" + root, type, null, List.of(new Predecessor(pred))), Void.class);
        restTemplate.postForEntity("/api/provenance",
            createTestRecord(unrelated, "data-" + unrelated, type, null, Collections.emptyList()), Void.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/provenance?dataType={t}&inChainOf={r}", String.class, type, root
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"totalElements\":2"), response.getBody());
        assertTrue(response.getBody().contains(root.toString()));
        assertTrue(response.getBody().contains(pred.toString()));
        assertTrue(!response.getBody().contains(unrelated.toString()));
    }

    @Test
    void verifyFileHashesEndpointReturns404WhenRecordNotFound() {
        UUID unknownId = UUID.randomUUID();
        List<FileHashInput> inputs = List.of(new FileHashInput("test.dat", "AAAA"));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/provenance/{id}/verify-files", inputs, String.class, unknownId
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private void createAndSaveRecord(String dataType, String signerIdentity) {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(id, "data-" + id, dataType, signerIdentity, Collections.emptyList(), null);
        restTemplate.postForEntity("/api/provenance", record, Void.class);
    }

    private void createAndSaveRecordWithAttributes(
        String dataType, String signerIdentity, Map<String, String> attributes
    ) {
        UUID id = UUID.randomUUID();
        ProvenanceRecord record = createTestRecord(
            id, "data-" + id, dataType, signerIdentity, Collections.emptyList(), attributes
        );
        restTemplate.postForEntity("/api/provenance", record, Void.class);
    }

    private ProvenanceRecord createTestRecord(UUID id, String dataId, String dataType, String signerIdentity, List<Predecessor> predecessors) {
        return createTestRecord(id, dataId, dataType, signerIdentity, predecessors, null);
    }

    private ProvenanceRecord createTestRecord(
        UUID id,
        String dataId,
        String dataType,
        String signerIdentity,
        List<Predecessor> predecessors,
        Map<String, String> attributes
    ) {
        Metadata metadata = new Metadata(dataId, dataType, predecessors, attributes);
        Manifest manifest = new Manifest("1", null, null);
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        SignatureDetails details = signerIdentity != null
            ? new SignatureDetails(signingTime, "12345", signerIdentity, "https://issuer.example.com", null)
            : null;
        ProvenanceSignature signature = new ProvenanceSignature(
            new byte[]{1, 2, 3},
            signingTime,
            HashAlgorithm.SHA256,
            details
        );
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature, null);
    }
}
