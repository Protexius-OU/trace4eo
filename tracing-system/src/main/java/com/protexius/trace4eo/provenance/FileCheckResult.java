package com.protexius.trace4eo.provenance;

public record FileCheckResult(
    String filename,
    String recordPath,
    FileCheckStatus status
) {}
