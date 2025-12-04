package com.guardtime.trace4eo.provenance.container.model;

public record Manifest(
    String version,
    FileHashInfo metadataHashInfo,
    FileHashInfo filesHashInfo
) implements RecordComponent {
    public Manifest(FileHashInfo metadataHashInfo, FileHashInfo filesHashInfo) {
        this("1", metadataHashInfo, filesHashInfo);
    }

}
