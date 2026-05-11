package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.sentinel2.Sentinel2HashCheckResult;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2TraceResponse;

import java.util.List;

public record Sentinel2HashCheckResponse(
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

    public static Sentinel2HashCheckResponse from(Sentinel2HashCheckResult result) {
        int matched = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2HashCheckResult.FileStatus.OK).count();
        int mismatched = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2HashCheckResult.FileStatus.HASH_MISMATCH).count();
        int notInTrace = (int) result.fileResults().stream()
            .filter(r -> r.status() == Sentinel2HashCheckResult.FileStatus.FILE_NOT_IN_TRACE).count();
        return new Sentinel2HashCheckResponse(
            result.traceStatus().name(),
            result.imageId(),
            result.trace().map(Sentinel2TraceResponse.Trace::id).orElse(null),
            result.trace().map(Sentinel2TraceResponse.Trace::hashAlgorithm).orElse(null),
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
        static FileResult from(Sentinel2HashCheckResult.FileResult r) {
            return new FileResult(r.filename(), r.status().name(), r.providedHash(), r.expectedHash());
        }
    }
}
