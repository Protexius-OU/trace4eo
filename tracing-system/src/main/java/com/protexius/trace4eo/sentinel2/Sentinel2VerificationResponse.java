package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.traceability.TraceVerificationResult;

public record Sentinel2VerificationResponse(
        String status,
        String imageId,
        String traceId,
        String hashAlgorithm,
        String signatureAlgorithm
) {

    public static Sentinel2VerificationResponse from(TraceVerificationResult result) {
        return new Sentinel2VerificationResponse(
                result.status().name(),
                result.imageId(),
                result.trace().map(t -> t.id()).orElse(null),
                result.trace().map(t -> t.hashAlgorithm()).orElse(null),
                result.trace().map(t -> t.signature().algorithm()).orElse(null)
        );
    }
}
