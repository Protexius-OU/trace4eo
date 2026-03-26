package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.record.FilesInfo;
import com.protexius.trace4eo.provenance.record.Manifest;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;

import java.util.UUID;

public record ProvenanceRecordImpl(
    UUID id,
    Metadata metadata,
    FilesInfo filesInfo,
    Manifest manifest,
    ProvenanceSignature signature
) implements ProvenanceRecord {
}
