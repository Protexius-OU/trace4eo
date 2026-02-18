package com.guardtime.trace4eo.provenance.record;

import com.guardtime.trace4eo.provenance.HashAlgorithm;

import java.nio.file.Path;
import java.util.HexFormat;

public record FileHashInfo(
    Path path,
    HashAlgorithm hashAlgorithm,
    byte[] hashValue
) {
    public FileHashInfo {
        if (path != null && path.isAbsolute()) {
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
            path, hashAlgorithm, HexFormat.of().formatHex(hashValue));
    }
}
