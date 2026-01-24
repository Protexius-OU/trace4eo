package com.guardtime.trace4eo.provenance.graph;

import java.util.List;
import java.util.UUID;

public record GraphMetadata(
    int totalNodes,
    int maxDepth,
    int requestedDepthLimit,
    boolean depthLimitReached,
    List<UUID> missingPredecessors
) {
}
