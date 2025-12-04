package com.guardtime.trace4eo.provenance.container.model;

import java.time.Instant;

public record ProvenanceSignature(
    byte[] bytes,
    Instant signingTime,
    HashAlgorithm hashAlgorithm
) {
}
