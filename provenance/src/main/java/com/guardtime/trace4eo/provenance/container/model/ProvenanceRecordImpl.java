package com.guardtime.trace4eo.provenance.container.model;

import java.util.UUID;

record ProvenanceRecordImpl(
    UUID id,
    Metadata metadata,
    FilesInfo filesInfo,
    Manifest manifest,
    ProvenanceSignature signature
) implements ProvenanceRecord {
}
