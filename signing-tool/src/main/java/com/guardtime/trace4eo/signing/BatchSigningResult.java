package com.guardtime.trace4eo.signing;

import java.nio.file.Path;
import java.util.List;

public record BatchSigningResult(
    int totalFiles,
    int successCount,
    int failureCount,
    List<FileSigningResult> results,
    Path outputPath
) {
    public boolean hasFailures() {
        return failureCount > 0;
    }

    public List<FileSigningResult> successfulResults() {
        return results.stream().filter(FileSigningResult::success).toList();
    }

    public List<FileSigningResult> failedResults() {
        return results.stream().filter(r -> !r.success()).toList();
    }
}
