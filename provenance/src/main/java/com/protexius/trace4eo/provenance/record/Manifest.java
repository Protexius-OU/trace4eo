package com.protexius.trace4eo.provenance.record;

public record Manifest(
    String version,
    FileHashInfo metadataHashInfo,
    FileHashInfo filesHashInfo
) implements RecordComponent {
    public Manifest(FileHashInfo metadataHashInfo, FileHashInfo filesHashInfo) {
        this("1", metadataHashInfo, filesHashInfo);
    }

}
