package com.protexius.trace4eo.provenance.traceability;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of verifying just the existence and signature of a Sentinel-2 traceability record, without
 * checking that any local file matches the trace contents. Used in contexts where the original
 * product file is not available (e.g. server-side verification when only the provenance record
 * was uploaded).
 */
public record TraceVerificationResult(Status status, Optional<TraceResponse.Trace> trace, String imageId) {

    public TraceVerificationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(trace);
        Objects.requireNonNull(imageId);
    }

    public static TraceVerificationResult ok(TraceResponse.Trace trace, String imageId) {
        return new TraceVerificationResult(Status.OK, Optional.of(trace), imageId);
    }

    public static TraceVerificationResult traceNotFound(String imageId) {
        return new TraceVerificationResult(Status.TRACE_NOT_FOUND, Optional.empty(), imageId);
    }

    public static TraceVerificationResult signatureError(TraceResponse.Trace trace, String imageId) {
        return new TraceVerificationResult(Status.SIGNATURE_ERROR, Optional.of(trace), imageId);
    }

    public enum Status {
        OK,
        TRACE_NOT_FOUND,
        SIGNATURE_ERROR
    }
}
