package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Testcontainers
class ProvenanceRegistryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Autowired
    private JdbcClient jdbcClient;

    private ProvenanceRegistry provenanceRegistry;

    @BeforeEach
    void setUp() {
        provenanceRegistry = new ProvenanceRegistry(jdbcClient, new ProvenanceJsonMapper());
    }

    @Test
    void addSignatureInsertsRecord() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        byte[] signatureBytes = new byte[]{1, 2, 3, 4, 5};

        provenanceRegistry.addSignature(id, signingTime, signatureBytes, "user@example.com");

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM signature WHERE id = :id")
            .param("id", id)
            .query(Integer.class)
            .single();
        assertEquals(1, count);
    }

    @Test
    void addSignatureStoresCorrectData() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        byte[] signatureBytes = new byte[]{1, 2, 3, 4, 5};

        provenanceRegistry.addSignature(id, signingTime, signatureBytes, "user@example.com");

        var result = jdbcClient.sql("SELECT signing_time, signature, signer_identity FROM signature WHERE id = :id")
            .param("id", id)
            .query((rs, rowNum) -> new Object[]{
                rs.getTimestamp("signing_time").toInstant(),
                rs.getBytes("signature"),
                rs.getString("signer_identity")
            })
            .single();

        assertEquals(signingTime, result[0]);
        assertNotNull(result[1]);
        assertEquals("user@example.com", result[2]);
    }

    @Test
    void addSignatureWithNullSignerIdentity() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        byte[] signatureBytes = new byte[]{1, 2, 3, 4, 5};

        provenanceRegistry.addSignature(id, signingTime, signatureBytes, null);

        String signerIdentity = jdbcClient.sql("SELECT signer_identity FROM signature WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .optional()
            .orElse(null);

        assertTrue(signerIdentity == null);
    }

    @Test
    void addProvenanceRecordInsertsRecord() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"test","dataType":"test-type","predecessors":[]}""",
            """
            {"files":[]}""",
            createdAt
        );

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM provenance_record WHERE id = :id")
            .param("id", id)
            .query(Integer.class)
            .single();
        assertEquals(1, count);
    }

    @Test
    void addProvenanceRecordStoresJsonbCorrectly() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String dataId = "data-" + id;
        String dataType = "type-" + id;
        String manifestJson = """
            {"version":"1","metadataHashInfo":null,"filesHashInfo":null}""";
        String metadataJson = """
            {"dataId":"%s","dataType":"%s","predecessors":[]}""".formatted(dataId, dataType);

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(id, manifestJson, metadataJson, null, createdAt);

        String storedMetadata = jdbcClient.sql("SELECT metadata::text FROM provenance_record WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();

        assertTrue(storedMetadata.contains(dataId));
        assertTrue(storedMetadata.contains(dataType));
    }

    @Test
    void addProvenanceRecordHandlesNullFilesJson() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"test","dataType":"test-type","predecessors":[]}""",
            null,
            createdAt
        );

        String files = jdbcClient.sql("SELECT files::text FROM provenance_record WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .optional()
            .orElse(null);

        assertTrue(files == null || files.equals("null"));
    }

    @Test
    void addVerificationLogInsertsRecord() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"test","dataType":"test-type","predecessors":[]}""",
            null,
            createdAt
        );

        provenanceRegistry.addVerificationLog(id, Instant.now(), true);

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM verification_log WHERE provenance_record_id = :id")
            .param("id", id)
            .query(Integer.class)
            .single();
        assertEquals(1, count);
    }

    @Test
    void addVerificationLogStoresCorrectStatus() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"test","dataType":"test-type","predecessors":[]}""",
            null,
            createdAt
        );

        provenanceRegistry.addVerificationLog(id, Instant.now(), false);

        Boolean status = jdbcClient.sql("SELECT status FROM verification_log WHERE provenance_record_id = :id")
            .param("id", id)
            .query(Boolean.class)
            .single();
        assertEquals(false, status);
    }

    @Test
    void multipleVerificationLogsCanBeAddedForSameRecord() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3}, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"test","dataType":"test-type","predecessors":[]}""",
            null,
            createdAt
        );

        provenanceRegistry.addVerificationLog(id, Instant.now(), true);
        provenanceRegistry.addVerificationLog(id, Instant.now(), false);
        provenanceRegistry.addVerificationLog(id, Instant.now(), true);

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM verification_log WHERE provenance_record_id = :id")
            .param("id", id)
            .query(Integer.class)
            .single();
        assertEquals(3, count);
    }

    @Test
    void getReturnsEmptyWhenRecordNotFound() {
        UUID id = UUID.randomUUID();

        Optional<ProvenanceRecord> result = provenanceRegistry.get(id);

        assertTrue(result.isEmpty());
    }

    @Test
    void getReturnsRecordWhenFound() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        String dataId = "data-" + id;
        String dataType = "type-" + id;
        byte[] signatureBytes = """
            {"bytes":"AQID","signingTime":"2024-01-15T10:30:00Z","hashAlgorithm":"SHA256"}""".getBytes();

        provenanceRegistry.addSignature(id, signingTime, signatureBytes, null);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1","metadataHashInfo":null,"filesHashInfo":null}""",
            """
            {"dataId":"%s","dataType":"%s","predecessors":[]}""".formatted(dataId, dataType),
            null,
            signingTime
        );

        Optional<ProvenanceRecord> result = provenanceRegistry.get(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
        assertEquals(dataId, result.get().metadata().dataId());
        assertEquals(dataType, result.get().metadata().dataType());
    }

    @Test
    void addSignatureThrowsOnDuplicateId() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.now();
        byte[] signatureBytes = new byte[]{1, 2, 3};

        provenanceRegistry.addSignature(id, signingTime, signatureBytes, null);

        assertThrows(Exception.class, () ->
            provenanceRegistry.addSignature(id, signingTime, signatureBytes, null)
        );
    }

    @Test
    void findDistinctDataTypesReturnsUniqueValues() {
        createRecordWithTypeAndSigner("type-a", "user1@example.com");
        createRecordWithTypeAndSigner("type-b", "user2@example.com");
        createRecordWithTypeAndSigner("type-a", "user3@example.com");

        List<String> dataTypes = provenanceRegistry.findDistinctDataTypes();

        assertEquals(2, dataTypes.size());
        assertTrue(dataTypes.contains("type-a"));
        assertTrue(dataTypes.contains("type-b"));
    }

    @Test
    void findDistinctSignerIdentitiesReturnsUniqueValues() {
        createRecordWithTypeAndSigner("type-a", "user1@example.com");
        createRecordWithTypeAndSigner("type-b", "user1@example.com");
        createRecordWithTypeAndSigner("type-c", "user2@example.com");

        List<String> signerIdentities = provenanceRegistry.findDistinctSignerIdentities();

        assertEquals(2, signerIdentities.size());
        assertTrue(signerIdentities.contains("user1@example.com"));
        assertTrue(signerIdentities.contains("user2@example.com"));
    }

    @Test
    void findAllWithDataTypeFilter() {
        createRecordWithTypeAndSigner("type-a", "user@example.com");
        createRecordWithTypeAndSigner("type-b", "user@example.com");
        createRecordWithTypeAndSigner("type-a", "user@example.com");

        List<ProvenanceRecord> results = provenanceRegistry.findAll(
            0, 20, List.of("type-a"), null, null
        );

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals("type-a", r.metadata().dataType()));
    }

    @Test
    void findAllWithMultipleDataTypeFilter() {
        createRecordWithTypeAndSigner("type-a", "user@example.com");
        createRecordWithTypeAndSigner("type-b", "user@example.com");
        createRecordWithTypeAndSigner("type-c", "user@example.com");

        List<ProvenanceRecord> results = provenanceRegistry.findAll(
            0, 20, List.of("type-a", "type-b"), null, null
        );

        assertEquals(2, results.size());
    }

    @Test
    void findAllWithSignerIdentityFilter() {
        createRecordWithTypeAndSigner("type-a", "user1@example.com");
        createRecordWithTypeAndSigner("type-a", "user2@example.com");
        createRecordWithTypeAndSigner("type-a", "user1@example.com");

        List<ProvenanceRecord> results = provenanceRegistry.findAll(
            0, 20, null, null, List.of("user1@example.com")
        );

        assertEquals(2, results.size());
    }

    @Test
    void findAllWithDataIdTextFilter() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant now = Instant.now();

        provenanceRegistry.addSignature(id1, now, createSignatureBytes(), "user@example.com");
        provenanceRegistry.addProvenanceRecord(id1,
            """
            {"version":"1"}""",
            """
            {"dataId":"satellite-image-001","dataType":"type-a","predecessors":[]}""",
            null, now);

        provenanceRegistry.addSignature(id2, now, createSignatureBytes(), "user@example.com");
        provenanceRegistry.addProvenanceRecord(id2,
            """
            {"version":"1"}""",
            """
            {"dataId":"ground-truth-001","dataType":"type-a","predecessors":[]}""",
            null, now);

        List<ProvenanceRecord> results = provenanceRegistry.findAll(
            0, 20, null, "satellite", null
        );

        assertEquals(1, results.size());
        assertEquals("satellite-image-001", results.get(0).metadata().dataId());
    }

    @Test
    void countWithFilters() {
        createRecordWithTypeAndSigner("type-a", "user1@example.com");
        createRecordWithTypeAndSigner("type-b", "user2@example.com");
        createRecordWithTypeAndSigner("type-a", "user2@example.com");

        assertEquals(2, provenanceRegistry.count(List.of("type-a"), null, null));
        assertEquals(2, provenanceRegistry.count(null, null, List.of("user2@example.com")));
        assertEquals(1, provenanceRegistry.count(List.of("type-a"), null, List.of("user1@example.com")));
    }

    private void createRecordWithTypeAndSigner(String dataType, String signerIdentity) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        provenanceRegistry.addSignature(id, now, createSignatureBytes(), signerIdentity);
        provenanceRegistry.addProvenanceRecord(
            id,
            """
            {"version":"1"}""",
            """
            {"dataId":"data-%s","dataType":"%s","predecessors":[]}""".formatted(id, dataType),
            null,
            now
        );
    }

    private byte[] createSignatureBytes() {
        return """
            {"bytes":"AQID","signingTime":"2024-01-15T10:30:00Z","hashAlgorithm":"SHA256"}""".getBytes();
    }
}
