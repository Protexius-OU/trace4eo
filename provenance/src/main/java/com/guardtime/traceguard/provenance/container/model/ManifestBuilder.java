package com.guardtime.traceguard.provenance.container.model;

import com.guardtime.ksi.hashing.HashAlgorithm;

import java.io.IOException;

public class ManifestBuilder {
    private final HashAlgorithm hashAlgorithm;
    private Metadata metadata;
    private FilesInfo filesInfo;

    public ManifestBuilder(HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public Manifest build() throws IOException {
        if (metadata == null) {
            throw new IllegalStateException("Metadata must not be null.");
        }
        if (filesInfo == null) {
            throw new IllegalStateException("FilesInfo must not be null.");
        }
        FileHashInfo metadata = this.metadata.toFileHashInfo(hashAlgorithm);
        FileHashInfo filesInfo = this.filesInfo.toFileHashInfo(hashAlgorithm);
        return new Manifest(metadata, filesInfo);
    }

    public ManifestBuilder withMetadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public ManifestBuilder withFilesInfo(FilesInfo filesInfo) {
        this.filesInfo = filesInfo;
        return this;
    }
}
