package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.SequencedSet;

public record FilesInfo(
    @JsonProperty("files") SequencedSet<FileHashInfo> files,
    @JsonIgnore FilesContext filesContext
) implements RecordComponent {
}
