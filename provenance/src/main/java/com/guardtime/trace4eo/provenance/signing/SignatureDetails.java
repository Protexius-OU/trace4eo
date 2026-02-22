package com.guardtime.trace4eo.provenance.signing;

import java.time.Instant;

public record SignatureDetails(
    Instant signingTime,
    String rekorLogIndex,
    String signerIdentity,
    String certificateIssuer,
    String manifestHash
) {}
