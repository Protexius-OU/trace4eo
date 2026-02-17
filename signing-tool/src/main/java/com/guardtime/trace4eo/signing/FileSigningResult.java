package com.guardtime.trace4eo.signing;

import java.nio.file.Path;
import java.util.UUID;

public record FileSigningResult(
    Path filePath,
    boolean success,
    UUID recordId,
    String errorMessage
) {
    public static FileSigningResult success(Path filePath, UUID recordId) {
        return new FileSigningResult(filePath, true, recordId, null);
    }

    public static FileSigningResult failure(Path filePath, String errorMessage) {
        return new FileSigningResult(filePath, false, null, errorMessage);
    }
}
