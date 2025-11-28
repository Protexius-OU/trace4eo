package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Manifest(
    @JsonProperty("version") String version,
    @JsonProperty("meta.json") FileHashInfo metadataHashInfo,
    @JsonProperty("files.json") FileHashInfo filesHashInfo
) implements RecordComponent {
    public Manifest(FileHashInfo metadataHashInfo, FileHashInfo filesHashInfo) {
        this("1", metadataHashInfo, filesHashInfo);
    }

}
