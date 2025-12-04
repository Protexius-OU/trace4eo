package com.guardtime.trace4eo.provenance.container.model;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.UUID;

@JsonDeserialize(as = ProvenanceRecordImpl.class)
public interface ProvenanceRecord {
    UUID id();
    Metadata metadata();
    FilesInfo filesInfo();
    Manifest manifest();
    ProvenanceSignature signature();
}
