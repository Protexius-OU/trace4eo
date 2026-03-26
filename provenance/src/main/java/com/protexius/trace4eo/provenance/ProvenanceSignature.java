package com.protexius.trace4eo.provenance;

import com.protexius.trace4eo.provenance.signing.SignatureDetails;

import java.time.Instant;
import java.util.Base64;

public record ProvenanceSignature(
    byte[] bytes,
    Instant signingTime,
    HashAlgorithm hashAlgorithm,
    SignatureDetails details
) {
    // Constructor for backwards compatibility (details may be null for old signatures)
    public ProvenanceSignature(byte[] bytes, Instant signingTime, HashAlgorithm hashAlgorithm) {
        this(bytes, signingTime, hashAlgorithm, null);
    }

    @Override
    public String toString() {
        return String.format("ProvenanceSignature[bytes=%s, signingTime=%s, hashAlgorithm=%s, details=%s]",
            formatBytes(), signingTime, hashAlgorithm, details);
    }

    private String formatBytes() {
        if (bytes == null) {
            return "null";
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        if (base64.length() <= 20) {
            return base64;
        }
        return String.format("%s...(%d bytes)", base64.substring(0, 20), bytes.length);
    }
}
