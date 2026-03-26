package com.protexius.trace4eo.provenance.record;

import com.protexius.trace4eo.provenance.HashAlgorithm;

import java.util.Base64;

public record FileHashInfo(
    String path,
    HashAlgorithm hashAlgorithm,
    byte[] hashValue
) {
    public FileHashInfo {
        if (path != null && path.startsWith("/")) {
            throw new IllegalArgumentException(
                String.format("Path must be relative to /<record-id>/files/ directory in zip. Is actually %s", path)
            );
        }
    }

    public FileHashInfo(HashAlgorithm hashAlgorithm, byte[] hashValue) {
        this(null, hashAlgorithm, hashValue);
    }

    @Override
    public String toString() {
        return String.format("FileHashInfo[path=%s, hashAlgorithm=%s, hashValue=%s]",
            path, hashAlgorithm, Base64.getEncoder().encodeToString(hashValue));
    }
}
