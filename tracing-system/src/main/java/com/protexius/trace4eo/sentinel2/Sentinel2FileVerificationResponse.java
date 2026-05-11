package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.traceability.Sentinel2FileHashCheckResult;
import com.protexius.trace4eo.provenance.traceability.TraceResponse;

public record Sentinel2FileVerificationResponse(
        String status,
        String imageId,
        String filename,
        String providedHash,
        String expectedHash,
        String traceId,
        String hashAlgorithm,
        String signatureAlgorithm
) {

    public static Sentinel2FileVerificationResponse from(Sentinel2FileHashCheckResult result) {
        return new Sentinel2FileVerificationResponse(
                result.status().name(),
                result.imageId(),
                result.filename(),
                result.providedHash(),
                result.expectedHash(),
                result.trace().map(TraceResponse.Trace::id).orElse(null),
                result.trace().map(TraceResponse.Trace::hashAlgorithm).orElse(null),
                result.trace().map(t -> t.signature().algorithm()).orElse(null)
        );
    }
}
