package com.guardtime.trace4eo.provenance.record;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;

public class ManifestBuilder {
    private final HashAlgorithm hashAlgorithm;
    private final ProvenanceJsonMapper provenanceJsonMapper;
    private Metadata metadata;
    private FilesInfo filesInfo;

    public ManifestBuilder(HashAlgorithm hashAlgorithm, ProvenanceJsonMapper provenanceJsonMapper) {
        this.hashAlgorithm = hashAlgorithm;
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    public Manifest build() throws IOException {
        if (metadata == null) {
            throw new IllegalStateException("Metadata must not be null.");
        }
        if (filesInfo == null) {
            throw new IllegalStateException("FilesInfo must not be null.");
        }
        byte[] manifestBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(metadata)).getEncodedUTF8();
        FileHashInfo metadata = new FileHashInfo(hashAlgorithm, manifestBytes);
        byte[] filesInfoBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(filesInfo)).getEncodedUTF8();
        FileHashInfo filesInfo = new FileHashInfo(hashAlgorithm, filesInfoBytes);
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
