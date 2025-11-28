package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Metadata(
    @JsonProperty("dataId") String dataId,
    @JsonProperty("dataType") String dataType,
    @JsonProperty("predecessors") List<Predecessor> predecessors
) implements RecordComponent {
}
