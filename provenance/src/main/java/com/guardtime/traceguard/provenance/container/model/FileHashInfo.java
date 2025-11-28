package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.guardtime.ksi.hashing.HashAlgorithm;

import java.nio.file.Path;
import java.util.Base64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileHashInfo(
        @JsonProperty("path") Path path,
        @JsonProperty("hashAlgorithm") HashAlgorithm hashAlgorithm,
        @JsonProperty("hashValue") byte[] hashValue
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

    @JsonGetter("path")
    public String getPath() {
        if (path == null) {
            return null;
        }
        // Return path as a string without "file://" prefix.
        return path.toString();
    }

    @JsonGetter
    public String getHashAlgorithm() {
        return hashAlgorithm == null ? null : hashAlgorithm.name();
    }

    @JsonGetter
    public String getHashValue() {
        return hashValue == null ? null : Base64.getEncoder().encodeToString(hashValue);
    }
}
