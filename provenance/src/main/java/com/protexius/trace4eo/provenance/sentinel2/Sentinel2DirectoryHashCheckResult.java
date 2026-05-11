package com.protexius.trace4eo.provenance.sentinel2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregated result of comparing many precomputed BLAKE3 file hashes against a single Copernicus
 * traceability record. The trace is fetched and its signature verified once; each input file is
 * looked up in the signed contents list (or matched against the top-level product hash) and gets
 * its own per-file status.
 */
public record Sentinel2DirectoryHashCheckResult(
        TraceStatus traceStatus,
        String imageId,
        Optional<Sentinel2TraceResponse.Trace> trace,
        List<FileResult> fileResults
) {

    public Sentinel2DirectoryHashCheckResult {
        Objects.requireNonNull(traceStatus);
        Objects.requireNonNull(imageId);
        Objects.requireNonNull(trace);
        Objects.requireNonNull(fileResults);
    }

    public static Sentinel2DirectoryHashCheckResult traceNotFound(String imageId) {
        return new Sentinel2DirectoryHashCheckResult(TraceStatus.TRACE_NOT_FOUND, imageId, Optional.empty(), List.of());
    }

    public static Sentinel2DirectoryHashCheckResult signatureError(Sentinel2TraceResponse.Trace trace, String imageId) {
        return new Sentinel2DirectoryHashCheckResult(TraceStatus.SIGNATURE_ERROR, imageId, Optional.of(trace), List.of());
    }

    public static Sentinel2DirectoryHashCheckResult ok(
            Sentinel2TraceResponse.Trace trace, String imageId, List<FileResult> fileResults
    ) {
        return new Sentinel2DirectoryHashCheckResult(TraceStatus.OK, imageId, Optional.of(trace), fileResults);
    }

    public enum TraceStatus { OK, TRACE_NOT_FOUND, SIGNATURE_ERROR }

    public record FileResult(
            String filename,
            FileStatus status,
            String providedHash,
            String expectedHash
    ) {
        public FileResult {
            Objects.requireNonNull(filename);
            Objects.requireNonNull(status);
            Objects.requireNonNull(providedHash);
        }
    }

    public enum FileStatus { OK, HASH_MISMATCH, FILE_NOT_IN_TRACE }
}
