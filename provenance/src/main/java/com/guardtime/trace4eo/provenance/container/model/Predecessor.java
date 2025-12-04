package com.guardtime.trace4eo.provenance.container.model;

import java.util.Objects;
import java.util.UUID;

public record Predecessor(UUID id) {
    public Predecessor {
        Objects.requireNonNull(id, "Predecessor ID must be set.");
    }
}
