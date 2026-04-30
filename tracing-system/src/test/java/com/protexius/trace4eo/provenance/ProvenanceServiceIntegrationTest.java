package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProvenanceServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3-alpine");

    @Autowired
    private ProvenanceService provenanceService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void saveAndRetrieveProvenanceRecord() {
        UUID id = UUID.randomUUID();
        String dataId = "data-" + id;
        String dataType = "type-" + id;
        ProvenanceRecord record = createTestRecord(id, dataId, dataType);

        provenanceService.saveSignature(record);
        provenanceService.saveProvenanceRecord(record, "uploader@example.com");

        Optional<ProvenanceRecord> retrieved = provenanceService.get(id);

        assertTrue(retrieved.isPresent());
        assertEquals(id, retrieved.get().id());
        assertEquals(dataId, retrieved.get().metadata().dataId());
        assertEquals(dataType, retrieved.get().metadata().dataType());
        assertEquals("uploader@example.com", retrieved.get().uploaderIdentity());
    }

    @Test
    void getReturnsEmptyForNonExistentRecord() {
        UUID id = UUID.randomUUID();

        Optional<ProvenanceRecord> retrieved = provenanceService.get(id);

        assertTrue(retrieved.isEmpty());
    }

    @Test
    void saveSignatureStoresSerializedJson() {
        UUID id = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        HashAlgorithm hashAlgorithm = HashAlgorithm.SHA256;
        ProvenanceRecord record = createTestRecord(id, signingTime, hashAlgorithm);

        provenanceService.saveSignature(record);

        String storedSignature = jdbcClient.sql("SELECT convert_from(signature, 'UTF8') FROM signature WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();

        assertTrue(storedSignature.contains(signingTime.toString()));
        assertTrue(storedSignature.contains(hashAlgorithm.name()));
    }

    @Test
    void saveProvenanceRecordStoresJsonbFields() {
        UUID id = UUID.randomUUID();
        String dataId = "data-" + id;
        String dataType = "type-" + id;
        ProvenanceRecord record = createTestRecord(id, dataId, dataType);

        provenanceService.saveSignature(record);
        provenanceService.saveProvenanceRecord(record, null);

        String storedMetadata = jdbcClient.sql("SELECT metadata::text FROM provenance_record WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();

        assertTrue(storedMetadata.contains(dataId));
        assertTrue(storedMetadata.contains(dataType));
    }

    private ProvenanceRecord createTestRecord(UUID id, String dataId, String dataType) {
        Metadata metadata = new Metadata(dataId, dataType, Collections.emptyList());
        Manifest manifest = new Manifest("1", null, null);
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");
        ProvenanceSignature signature = new ProvenanceSignature(
            new byte[]{1, 2, 3},
            signingTime,
            HashAlgorithm.SHA256
        );
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature, null);
    }

    private ProvenanceRecord createTestRecord(UUID id, Instant signingTime, HashAlgorithm hashAlgorithm) {
        Metadata metadata = new Metadata("data-id", "data-type", Collections.emptyList());
        Manifest manifest = new Manifest("1", null, null);
        ProvenanceSignature signature = new ProvenanceSignature(
            new byte[]{1, 2, 3},
            signingTime,
            hashAlgorithm
        );
        return new ProvenanceRecordImpl(id, metadata, null, manifest, signature, null);
    }
}
