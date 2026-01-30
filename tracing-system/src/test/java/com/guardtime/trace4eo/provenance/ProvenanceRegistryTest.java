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

        provenanceRegistry.addSignature(id, signingTime, signatureBytes);

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

        provenanceRegistry.addSignature(id, signingTime, signatureBytes);

        var result = jdbcClient.sql("SELECT signing_time, signature FROM signature WHERE id = :id")
            .param("id", id)
            .query((rs, rowNum) -> new Object[]{
                rs.getTimestamp("signing_time").toInstant(),
                rs.getBytes("signature")
            })
            .single();

        assertEquals(signingTime, result[0]);
        assertNotNull(result[1]);
    }

    @Test
    void addProvenanceRecordInsertsRecord() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, createdAt, new byte[]{1, 2, 3});
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

        provenanceRegistry.addSignature(id, signingTime, signatureBytes);
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

        provenanceRegistry.addSignature(id, signingTime, signatureBytes);

        assertThrows(Exception.class, () ->
            provenanceRegistry.addSignature(id, signingTime, signatureBytes)
        );
    }
}
