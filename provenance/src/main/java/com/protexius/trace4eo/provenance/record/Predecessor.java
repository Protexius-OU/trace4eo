package com.protexius.trace4eo.provenance.record;

import java.util.Objects;
import java.util.UUID;

public record Predecessor(UUID id) {
    public Predecessor {
        Objects.requireNonNull(id, "Predecessor ID must be set.");
    }
}
