package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.traceability.Sentinel2DirectoryHashCheckResult;

import java.util.List;

public record Sentinel2DirectoryVerificationResponse(
        String traceStatus,
        String imageId,
        String traceId,
        String hashAlgorithm,
        String signatureAlgorithm,
        int totalFiles,
        int matchedFiles,
        int mismatchedFiles,
        int filesNotInTrace,
        List<FileResult> fileResults
) {

    public static Sentinel2DirectoryVerificationResponse from(Sentinel2DirectoryHashCheckResult result) {
        int matched = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2DirectoryHashCheckResult.FileStatus.OK).count();
        int mismatched = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2DirectoryHashCheckResult.FileStatus.HASH_MISMATCH).count();
        int notInTrace = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2DirectoryHashCheckResult.FileStatus.FILE_NOT_IN_TRACE).count();
        return new Sentinel2DirectoryVerificationResponse(
            result.traceStatus().name(),
            result.imageId(),
            result.trace().map(t -> t.id()).orElse(null),
            result.trace().map(t -> t.hashAlgorithm()).orElse(null),
            result.trace().map(t -> t.signature().algorithm()).orElse(null),
            result.fileResults().size(),
            matched,
            mismatched,
            notInTrace,
            result.fileResults().stream().map(FileResult::from).toList()
        );
    }

    public record FileResult(
            String filename,
            String status,
            String providedHash,
            String expectedHash
    ) {
        static FileResult from(Sentinel2DirectoryHashCheckResult.FileResult r) {
            return new FileResult(r.filename(), r.status().name(), r.providedHash(), r.expectedHash());
        }
    }
}
