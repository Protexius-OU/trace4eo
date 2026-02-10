package com.guardtime.trace4eo.provenance.graph;

import java.util.List;
import java.util.UUID;

public record GraphMetadata(
    int totalNodes,
    int maxDepth,
    List<UUID> missingPredecessors
) {
}
