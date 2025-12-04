package com.guardtime.trace4eo.provenance.container.model;

import com.guardtime.trace4eo.provenance.container.signing.SignatureUtil;

import java.io.IOException;
import java.util.UUID;

public class ProvenanceRecordBuilder {
    private Metadata metadata;
    private FilesInfo filesInfo;
    private Manifest manifest;
    private ProvenanceSignature signature;

    public ProvenanceRecordBuilder() {
    }

    public ProvenanceRecord build() throws IOException {
        if (metadata == null) {
            throw new IllegalStateException("Metadata must not be null.");
        }
        if (filesInfo == null) {
            throw new IllegalStateException("FilesInfo must not be null.");
        }
        if (manifest == null) {
            throw new IllegalStateException("Manifest must not be null.");
        }
        if (signature == null) {
            throw new IllegalStateException("Signature must not be null.");
        }
        UUID provenanceRecordId = SignatureUtil.createUuid(signature.bytes(), signature.signingTime().toEpochMilli());
        return new ProvenanceRecordImpl(provenanceRecordId, metadata, filesInfo, manifest, signature);
    }

    public ProvenanceRecordBuilder withMetadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public ProvenanceRecordBuilder withFilesInfo(FilesInfo filesInfo) {
        this.filesInfo = filesInfo;
        return this;
    }

    public ProvenanceRecordBuilder withManifest(Manifest manifest) {
        this.manifest = manifest;
        return this;
    }

    public ProvenanceRecordBuilder withSignature(ProvenanceSignature signature) {
        this.signature = signature;
        return this;
    }
}
