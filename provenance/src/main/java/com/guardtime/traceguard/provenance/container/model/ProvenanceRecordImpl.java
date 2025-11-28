package com.guardtime.traceguard.provenance.container.model;

import com.guardtime.ksi.unisignature.KSISignature;

import java.util.UUID;

record ProvenanceRecordImpl(
    UUID id,
    Metadata metadata,
    FilesInfo filesInfo,
    Manifest manifest,
    KSISignature signature
) implements ProvenanceRecord {
}
