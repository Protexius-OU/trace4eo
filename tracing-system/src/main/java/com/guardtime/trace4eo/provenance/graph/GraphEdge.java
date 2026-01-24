package com.guardtime.trace4eo.provenance.graph;

import java.util.Objects;
import java.util.UUID;

public record GraphEdge(
    UUID sourceId,
    UUID targetId
) {
    public GraphEdge {
        Objects.requireNonNull(sourceId, "Source ID must be set.");
        Objects.requireNonNull(targetId, "Target ID must be set.");
    }
}
