package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.guardtime.ksi.unisignature.KSISignature;

import java.util.UUID;

@JsonDeserialize(as = ProvenanceRecordImpl.class)
public interface ProvenanceRecord {
    UUID id();
    Metadata metadata();
    FilesInfo filesInfo();
    Manifest manifest();
    @JsonIgnore KSISignature signature();
}
