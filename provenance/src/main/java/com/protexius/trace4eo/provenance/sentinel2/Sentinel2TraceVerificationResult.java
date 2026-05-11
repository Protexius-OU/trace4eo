package com.protexius.trace4eo.provenance.sentinel2;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of verifying just the existence and signature of a Sentinel-2 traceability record, without
 * checking that any local file matches the trace contents. Used in contexts where the original
 * product file is not available (e.g. server-side verification when only the provenance record
 * was uploaded).
 */
public record Sentinel2TraceVerificationResult(Status status, Optional<Sentinel2TraceResponse.Trace> trace, String imageId) {

    public Sentinel2TraceVerificationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(trace);
        Objects.requireNonNull(imageId);
    }

    public static Sentinel2TraceVerificationResult ok(Sentinel2TraceResponse.Trace trace, String imageId) {
        return new Sentinel2TraceVerificationResult(Status.OK, Optional.of(trace), imageId);
    }

    public static Sentinel2TraceVerificationResult traceNotFound(String imageId) {
        return new Sentinel2TraceVerificationResult(Status.TRACE_NOT_FOUND, Optional.empty(), imageId);
    }

    public static Sentinel2TraceVerificationResult signatureError(Sentinel2TraceResponse.Trace trace, String imageId) {
        return new Sentinel2TraceVerificationResult(Status.SIGNATURE_ERROR, Optional.of(trace), imageId);
    }

    public enum Status {
        OK,
        TRACE_NOT_FOUND,
        SIGNATURE_ERROR
    }
}
