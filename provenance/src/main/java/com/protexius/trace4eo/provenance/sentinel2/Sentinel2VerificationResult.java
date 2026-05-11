package com.protexius.trace4eo.provenance.sentinel2;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public record Sentinel2VerificationResult(Status status, Optional<Sentinel2TraceResponse.Trace> trace, FileInfo localFile) {

    public static Sentinel2VerificationResult traceNotFound(String imageId, Path path) {
        return new Sentinel2VerificationResult(
                Status.TRACE_NOT_FOUND, Optional.empty(), new FileInfo(imageId, path, null)
        );
    }

    public static Sentinel2VerificationResult signatureError(Sentinel2TraceResponse.Trace trace, String imageId, Path path) {
        return new Sentinel2VerificationResult(
                Status.SIGNATURE_ERROR, Optional.of(trace), new FileInfo(imageId, path, null)
        );
    }

    public static Sentinel2VerificationResult hashMismatch(
        Sentinel2TraceResponse.Trace trace, String imageId, Path path, byte[] fileHash
    ) {
        return new Sentinel2VerificationResult(
                Status.HASH_MISMATCH, Optional.of(trace), new FileInfo(imageId, path, HexFormat.of().formatHex(fileHash))
        );
    }

    public static Sentinel2VerificationResult ok(Sentinel2TraceResponse.Trace trace, String imageId, Path path) {
        return new Sentinel2VerificationResult(
                Status.OK, Optional.of(trace), new FileInfo(imageId, path, null)
        );
    }

    public Sentinel2VerificationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(localFile);
    }

    public record FileInfo(String imageId, Path path, String hashHex) {
        public FileInfo {
            Objects.requireNonNull(imageId);
            Objects.requireNonNull(path);
        }
    }

    public enum Status {
        OK,
        TRACE_NOT_FOUND,
        // TODO - this could be more fine-grained
        SIGNATURE_ERROR,
        HASH_MISMATCH
    }
}
