package com.protexius.trace4eo.provenance.sentinel2;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of comparing a precomputed BLAKE3 hash of a Sentinel-2 product file against the
 * Copernicus traceability record's signed hashes. Used by the UI flow where the browser hashes
 * the file locally and sends only the hash to the backend.
 */
public record Sentinel2FileHashCheckResult(
        Status status,
        String imageId,
        String filename,
        String providedHash,
        String expectedHash,
        Optional<Sentinel2TraceResponse.Trace> trace
) {

    public Sentinel2FileHashCheckResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(imageId);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(providedHash);
        Objects.requireNonNull(trace);
    }

    public static Sentinel2FileHashCheckResult ok(
            Sentinel2TraceResponse.Trace trace, String imageId, String filename,
            String providedHash, String expectedHash
    ) {
        return new Sentinel2FileHashCheckResult(
                Status.OK, imageId, filename, providedHash, expectedHash, Optional.of(trace));
    }

    public static Sentinel2FileHashCheckResult hashMismatch(
            Sentinel2TraceResponse.Trace trace, String imageId, String filename,
            String providedHash, String expectedHash
    ) {
        return new Sentinel2FileHashCheckResult(
                Status.HASH_MISMATCH, imageId, filename, providedHash, expectedHash, Optional.of(trace));
    }

    public static Sentinel2FileHashCheckResult fileNotInTrace(
            Sentinel2TraceResponse.Trace trace, String imageId, String filename, String providedHash
    ) {
        return new Sentinel2FileHashCheckResult(
                Status.FILE_NOT_IN_TRACE, imageId, filename, providedHash, null, Optional.of(trace));
    }

    public static Sentinel2FileHashCheckResult traceNotFound(
            String imageId, String filename, String providedHash
    ) {
        return new Sentinel2FileHashCheckResult(
                Status.TRACE_NOT_FOUND, imageId, filename, providedHash, null, Optional.empty());
    }

    public static Sentinel2FileHashCheckResult signatureError(
            Sentinel2TraceResponse.Trace trace, String imageId, String filename, String providedHash
    ) {
        return new Sentinel2FileHashCheckResult(
                Status.SIGNATURE_ERROR, imageId, filename, providedHash, null, Optional.of(trace));
    }

    public enum Status {
        OK,
        TRACE_NOT_FOUND,
        SIGNATURE_ERROR,
        HASH_MISMATCH,
        FILE_NOT_IN_TRACE
    }
}
