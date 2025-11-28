package com.guardtime.traceguard.provenance.container.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

public record Predecessor(
    @JsonProperty("id") UUID id
) {
    public Predecessor {
        Objects.requireNonNull(id, "Predecessor ID must be set.");
    }
}
