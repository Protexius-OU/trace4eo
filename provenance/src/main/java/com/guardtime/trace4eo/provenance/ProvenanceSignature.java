package com.guardtime.trace4eo.provenance;

import java.time.Instant;

public record ProvenanceSignature(
    byte[] bytes,
    Instant signingTime,
    HashAlgorithm hashAlgorithm
) {
}
