package com.protexius.trace4eo.provenance.graph;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GraphNode(
    UUID id,
    String dataId,
    String dataType,
    Instant signingTime,
    int depth,
    int predecessorCount,
    String signerIdentity
) {
    public GraphNode {
        Objects.requireNonNull(id, "Node ID must be set.");
    }
}
