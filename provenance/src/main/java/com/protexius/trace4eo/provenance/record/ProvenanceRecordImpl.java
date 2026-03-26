package com.protexius.trace4eo.provenance.record;

import com.protexius.trace4eo.provenance.ProvenanceSignature;

import java.util.UUID;

record ProvenanceRecordImpl(
    UUID id,
    Metadata metadata,
    FilesInfo filesInfo,
    Manifest manifest,
    ProvenanceSignature signature
) implements ProvenanceRecord {
}
