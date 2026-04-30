package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class ProvenanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceRegistry.class);

    private final JdbcClient jdbcClient;
    private final ProvenanceJsonMapper jsonMapper;

    public ProvenanceRegistry(JdbcClient jdbcClient, ProvenanceJsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    public void addSignature(
        UUID id,
        Instant signingTime,
        byte[] signatureBytes,
        String signerIdentity
    ) {
        int rowsUpdated = jdbcClient.sql("""
                insert into signature (id, signing_time, signature, signer_identity)
                values (:id, :signing_time, :signature, :signer_identity)
                """)
            .param("id", id)
            .param("signing_time", Timestamp.from(signingTime))
            .param("signature", signatureBytes)
            .param("signer_identity", signerIdentity)
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
        Instant createdAt,
        String uploaderIdentity
    ) {
        int rowsUpdated = jdbcClient.sql("""
                insert into provenance_record (id, manifest, metadata, files, created_at, uploader_identity)
                values (:id, :manifest::jsonb, :metadata::jsonb, :files::jsonb, :created_at, :uploader_identity)
                """)
            .param("id", id)
            .param("manifest", manifestJson)
            .param("metadata", metadataJson)
            .param("files", filesJson)
            .param("created_at", Timestamp.from(createdAt))
            .param("uploader_identity", uploaderIdentity)
            .update();
        if (rowsUpdated != 1) {
            String message = String.format("Expected to update exactly one row, updated %s instead", rowsUpdated);
            throw new IllegalStateException(message);
        }
    }

    public List<UUID> findMissing(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = Set.copyOf(ids);
        List<UUID> existing = jdbcClient.sql(
                "select id from provenance_record where id in (:ids)")
            .param("ids", uniqueIds)
            .query(UUID.class)
            .list();
        return uniqueIds.stream()
            .filter(id -> !existing.contains(id))
            .toList();
    }

    public Optional<ProvenanceRecord> get(UUID id) {
        return jdbcClient.sql("""
                select
                    pr.id,
                    pr.manifest,
                    pr.metadata,
                    pr.files,
                    pr.uploader_identity,
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
            .query(this::mapRow)
            .optional();
    }

    public List<ProvenanceRecord> findAll(
        int page,
        int size,
        List<String> dataTypes,
        String dataId,
        List<String> signerIdentities
    ) {
        StringBuilder sql = new StringBuilder("""
            select
                pr.id,
                pr.manifest,
                pr.metadata,
                pr.files,
                pr.uploader_identity,
                s.signature
            from provenance_record pr
            inner join signature s on pr.id = s.id
            where 1=1
            """);

        appendFilters(sql, dataTypes, dataId, signerIdentities);
        sql.append(" order by pr.created_at desc");
        sql.append(" limit :limit offset :offset");

        var query = jdbcClient.sql(sql.toString())
            .param("limit", size)
            .param("offset", page * size);

        query = bindFilterParams(query, dataTypes, dataId, signerIdentities);
        return query.query(this::mapRow).list();
    }

    public long count(List<String> dataTypes, String dataId, List<String> signerIdentities) {
        StringBuilder sql = new StringBuilder("""
            select count(*) from provenance_record pr
            inner join signature s on pr.id = s.id
            where 1=1
            """);

        appendFilters(sql, dataTypes, dataId, signerIdentities);
        var query = jdbcClient.sql(sql.toString());
        query = bindFilterParams(query, dataTypes, dataId, signerIdentities);
        return query.query(Long.class).single();
    }

    public List<String> findDistinctDataTypes() {
        return jdbcClient.sql("""
                select distinct pr.metadata->>'dataType' as data_type
                from provenance_record pr
                where pr.metadata->>'dataType' is not null
                order by data_type
                """)
            .query(String.class)
            .list();
    }

    public List<String> findDistinctSignerIdentities() {
        return jdbcClient.sql("""
                select distinct s.signer_identity
                from signature s
                where s.signer_identity is not null
                order by s.signer_identity
                """)
            .query(String.class)
            .list();
    }

    private void appendFilters(
        StringBuilder sql,
        List<String> dataTypes,
        String dataId,
        List<String> signerIdentities
    ) {
        if (dataTypes != null && !dataTypes.isEmpty()) {
            sql.append(" and pr.metadata->>'dataType' in (:dataTypes)");
        }
        if (dataId != null && !dataId.isBlank()) {
            sql.append(" and pr.metadata->>'dataId' ilike :dataId");
        }
        if (signerIdentities != null && !signerIdentities.isEmpty()) {
            sql.append(" and s.signer_identity in (:signerIdentities)");
        }
    }

    private JdbcClient.StatementSpec bindFilterParams(
        JdbcClient.StatementSpec query,
        List<String> dataTypes,
        String dataId,
        List<String> signerIdentities
    ) {
        if (dataTypes != null && !dataTypes.isEmpty()) {
            query = query.param("dataTypes", dataTypes);
        }
        if (dataId != null && !dataId.isBlank()) {
            query = query.param("dataId", "%" + dataId + "%");
        }
        if (signerIdentities != null && !signerIdentities.isEmpty()) {
            query = query.param("signerIdentities", signerIdentities);
        }
        return query;
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

    private ProvenanceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Manifest manifest = jsonMapper.readValue(rs.getString("manifest"), Manifest.class);
        Metadata metadata = jsonMapper.readValue(rs.getString("metadata"), Metadata.class);
        String filesJson = rs.getString("files");
        FilesInfo filesInfo = filesJson != null
            ? jsonMapper.readValue(filesJson, FilesInfo.class)
            : null;
        String signatureJson = new String(rs.getBytes("signature"), StandardCharsets.UTF_8);
        ProvenanceSignature signature = jsonMapper.readValue(signatureJson, ProvenanceSignature.class);
        return new ProvenanceRecordImpl(
            UUID.fromString(rs.getString("id")),
            metadata,
            filesInfo,
            manifest,
            signature,
            rs.getString("uploader_identity")
        );
    }
}
