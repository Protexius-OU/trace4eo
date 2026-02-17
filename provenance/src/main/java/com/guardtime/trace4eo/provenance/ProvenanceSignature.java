package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.signing.SignatureDetails;

import java.time.Instant;

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
}
