package com.protexius.trace4eo.provenance.traceability;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public record VerificationResult(Status status, Optional<TraceResponse.Trace> trace, FileInfo localFile) {

    public static VerificationResult traceNotFound(String imageId, Path path) {
        return new VerificationResult(
                Status.TRACE_NOT_FOUND, Optional.empty(), new FileInfo(imageId, path, null)
        );
    }

    public static VerificationResult signatureError(TraceResponse.Trace trace, String imageId, Path path) {
        return new VerificationResult(
                Status.SIGNATURE_ERROR, Optional.of(trace), new FileInfo(imageId, path, null)
        );
    }

    public static VerificationResult hashMismatch(TraceResponse.Trace trace, String imageId, Path path, byte[] fileHash) {
        return new VerificationResult(
                Status.HASH_MISMATCH, Optional.of(trace), new FileInfo(imageId, path, HexFormat.of().formatHex(fileHash))
        );
    }

    public static VerificationResult ok(TraceResponse.Trace trace, String imageId, Path path) {
        return new VerificationResult(
                Status.OK, Optional.of(trace), new FileInfo(imageId, path, null)
        );
    }

    public VerificationResult {
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
