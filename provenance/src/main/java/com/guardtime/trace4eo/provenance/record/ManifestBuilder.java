package com.guardtime.trace4eo.provenance.record;

import com.guardtime.trace4eo.provenance.HashAlgorithm;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlgorithm.getName());

            byte[] metadataBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(metadata)).getEncodedUTF8();
            byte[] metadataHash = md.digest(metadataBytes);
            FileHashInfo metadataHashInfo = new FileHashInfo(hashAlgorithm, metadataHash);

            md.reset();

            byte[] filesInfoBytes = new JsonCanonicalizer(provenanceJsonMapper.writeValueAsBytes(filesInfo)).getEncodedUTF8();
            byte[] filesInfoHash = md.digest(filesInfoBytes);
            FileHashInfo filesInfoHashInfo = new FileHashInfo(hashAlgorithm, filesInfoHash);

            return new Manifest(metadataHashInfo, filesInfoHashInfo);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unsupported hash algorithm: " + hashAlgorithm.getName(), e);
        }
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
