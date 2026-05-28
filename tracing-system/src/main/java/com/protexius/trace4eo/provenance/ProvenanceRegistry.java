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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                where pr.id = :id
                """)
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    public Map<UUID, ProvenanceRecord> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uniqueIds = Set.copyOf(ids);
        List<ProvenanceRecord> records = jdbcClient.sql("""
                select
                    pr.id,
                    pr.manifest,
                    pr.metadata,
                    pr.files,
                    pr.uploader_identity,
                    s.signature
                from provenance_record pr
                inner join signature s on pr.id = s.id
                where pr.id in (:ids)
                """)
            .param("ids", uniqueIds)
            .query(this::mapRow)
            .list();
        Map<UUID, ProvenanceRecord> byId = new HashMap<>(records.size());
        for (ProvenanceRecord record : records) {
            byId.put(record.id(), record);
        }
        return byId;
    }

    public List<ProvenanceRecord> findAll(int page, int size, RecordFilterCriteria criteria) {
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

        appendFilters(sql, criteria);
        sql.append(" order by pr.created_at desc");
        sql.append(" limit :limit offset :offset");

        var query = jdbcClient.sql(sql.toString())
            .param("limit", size)
            .param("offset", page * size);

        query = bindFilterParams(query, criteria);
        return query.query(this::mapRow).list();
    }

    public long count(RecordFilterCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
            select count(*) from provenance_record pr
            inner join signature s on pr.id = s.id
            where 1=1
            """);

        appendFilters(sql, criteria);
        var query = jdbcClient.sql(sql.toString());
        query = bindFilterParams(query, criteria);
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

    public List<LocationCount> countByLocationForIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                select country, count(*) as record_count
                from (
                    select lower(trim(pr.metadata->'attributes'->>'location')) as country
                    from provenance_record pr
                    where pr.id in (:ids)
                ) loc
                where country is not null
                  and country <> ''
                group by country
                order by record_count desc, country asc
                """)
            .param("ids", Set.copyOf(ids))
            .query((rs, rowNum) -> new LocationCount(
                rs.getString("country"),
                rs.getLong("record_count")
            ))
            .list();
    }

    private void appendFilters(StringBuilder sql, RecordFilterCriteria criteria) {
        if (criteria.dataTypes() != null && !criteria.dataTypes().isEmpty()) {
            sql.append(" and pr.metadata->>'dataType' in (:dataTypes)");
        }
        if (criteria.dataId() != null && !criteria.dataId().isBlank()) {
            sql.append(" and pr.metadata->>'dataId' ilike :dataId");
        }
        if (criteria.signerIdentities() != null && !criteria.signerIdentities().isEmpty()) {
            sql.append(" and s.signer_identity in (:signerIdentities)");
        }
        AttributeFilter attributes = criteria.attributes();
        if (attributes != null && !attributes.isEmpty()) {
            int size = attributes.keyToValues().size();
            for (int i = 0; i < size; i++) {
                sql.append(" and lower(pr.metadata->'attributes'->>:attrKey_").append(i)
                    .append(") in (:attrValues_").append(i).append(")");
            }
        }
        if (criteria.recordIds() != null) {
            sql.append(" and pr.id in (:recordIds)");
        }
    }

    private JdbcClient.StatementSpec bindFilterParams(
        JdbcClient.StatementSpec query,
        RecordFilterCriteria criteria
    ) {
        if (criteria.dataTypes() != null && !criteria.dataTypes().isEmpty()) {
            query = query.param("dataTypes", criteria.dataTypes());
        }
        if (criteria.dataId() != null && !criteria.dataId().isBlank()) {
            query = query.param("dataId", "%" + criteria.dataId() + "%");
        }
        if (criteria.signerIdentities() != null && !criteria.signerIdentities().isEmpty()) {
            query = query.param("signerIdentities", criteria.signerIdentities());
        }
        AttributeFilter attributes = criteria.attributes();
        if (attributes != null && !attributes.isEmpty()) {
            int index = 0;
            for (Map.Entry<String, List<String>> entry : attributes.keyToValues().entrySet()) {
                query = query.param("attrKey_" + index, entry.getKey());
                query = query.param("attrValues_" + index, lowercased(entry.getValue()));
                index++;
            }
        }
        if (criteria.recordIds() != null) {
            query = query.param("recordIds", criteria.recordIds());
        }
        return query;
    }

    private List<String> lowercased(List<String> values) {
        return values.stream()
            .map(v -> v == null ? null : v.toLowerCase(Locale.ROOT))
            .toList();
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
