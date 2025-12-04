package com.guardtime.trace4eo.provenance.record;

import com.guardtime.trace4eo.provenance.HashAlgorithm;

import java.nio.file.Path;

public record FileHashInfo(
    Path path,
    HashAlgorithm hashAlgorithm,
    byte[] hashValue
) {
    public FileHashInfo {
        if (path != null && path.isAbsolute()) {
            throw new IllegalArgumentException(
                "Path must be relative to /<record-id>/files/ directory in zip. Is actually " + path
            );
        }
    }

    public FileHashInfo(HashAlgorithm hashAlgorithm, byte[] hashValue) {
        this(null, hashAlgorithm, hashValue);
    }
}
