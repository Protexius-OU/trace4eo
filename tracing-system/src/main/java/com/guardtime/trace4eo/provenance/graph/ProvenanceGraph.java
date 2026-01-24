package com.guardtime.trace4eo.provenance.graph;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ProvenanceGraph(
    UUID rootId,
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    GraphMetadata metadata
) {
    public ProvenanceGraph {
        Objects.requireNonNull(rootId, "Root ID must be set.");
        Objects.requireNonNull(nodes, "Nodes must be set.");
        Objects.requireNonNull(edges, "Edges must be set.");
        Objects.requireNonNull(metadata, "Metadata must be set.");
    }
}
