package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.record.FilesInfo;
import com.guardtime.trace4eo.provenance.record.Manifest;
import com.guardtime.trace4eo.provenance.record.Metadata;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProvenanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceRegistry.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcClient jdbcClient;

    public ProvenanceRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void addSignature(
        UUID id,
        Instant signingTime,
        byte[] signatureBytes
    ) {
        int rowsUpdated = jdbcClient.sql("""
                insert into signature (id, signing_time, signature)
                values (:id, :signing_time, :signature)
                """)
            .param("id", id)
            .param("signing_time", Timestamp.from(signingTime))
            .param("signature", signatureBytes)
            .update();
        if (rowsUpdated != 1) {
            String message = String.format("Expected to update exactly one row, updated %s instead", rowsUpdated);
            throw new IllegalStateException(message);
        }
    }

    public void addProvenanceRecord(
        UUID id,
        String manifestJson,
        String metadataJson,
        String filesJson,
        Instant createdAt
    ) {
        int rowsUpdated = jdbcClient.sql("""
                insert into provenance_record (id, manifest, metadata, files, created_at)
                values (:id, :manifest::jsonb, :metadata::jsonb, :files::jsonb, :created_at)
                """)
            .param("id", id)
            .param("manifest", manifestJson)
            .param("metadata", metadataJson)
            .param("files", filesJson)
            .param("created_at", Timestamp.from(createdAt))
            .update();
        if (rowsUpdated != 1) {
            String message = String.format("Expected to update exactly one row, updated %s instead", rowsUpdated);
            throw new IllegalStateException(message);
        }
    }

    public Optional<ProvenanceRecord> get(UUID id) {
        return jdbcClient.sql("""
                select
                    pr.id,
                    pr.manifest,
                    pr.metadata,
                    pr.files,
                    s.signature
                from provenance_record pr
                inner join signature s on pr.id = s.id
                left join (
                    select *
                    from verification_log v where v.provenance_record_id = :id
                    order by created_at desc
                    limit 1
                ) vl on pr.id = vl.provenance_record_id
                where pr.id = :id
                """)
            .param("id", id)
            .query(provenanceRecordRowMapper)
            .optional();
    }

    public void addVerificationLog(UUID provenanceRecordId, Instant createdAt, boolean status) {
        int rowsUpdated = jdbcClient.sql("""
                insert into verification_log (provenance_record_id, created_at, status)
                values (:provenance_record_id, :created_at, :status)
                """)
            .param("provenance_record_id", provenanceRecordId)
            .param("created_at", Timestamp.from(createdAt))
            .param("status", status)
            .update();
        if (rowsUpdated != 1) {
            String message = String.format("Expected to update exactly one row, updated %s instead", rowsUpdated);
            throw new IllegalStateException(message);
        }
    }

    private final RowMapper<ProvenanceRecord> provenanceRecordRowMapper = (rs, rowNum) -> {
        Manifest manifest = OBJECT_MAPPER.readValue(rs.getString("manifest"), Manifest.class);
        Metadata metadata = OBJECT_MAPPER.readValue(rs.getString("metadata"), Metadata.class);
        String filesJson = rs.getString("files");
        FilesInfo filesInfo = filesJson != null
            ? OBJECT_MAPPER.readValue(filesJson, FilesInfo.class)
            : null;
        String signatureJson = new String(rs.getBytes("signature"), StandardCharsets.UTF_8);
        ProvenanceSignature signature = OBJECT_MAPPER.readValue(signatureJson, ProvenanceSignature.class);
        return new ProvenanceRecordImpl(
            UUID.fromString(rs.getString("id")),
            metadata,
            filesInfo,
            manifest,
            signature
        );
    };
}
