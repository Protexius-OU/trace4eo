package com.protexius.trace4eo.provenance.graph;

import com.protexius.trace4eo.provenance.ProvenanceRegistry;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ProvenanceGraphService {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceGraphService.class);

    private final ProvenanceRegistry provenanceRegistry;

    public ProvenanceGraphService(ProvenanceRegistry provenanceRegistry) {
        this.provenanceRegistry = provenanceRegistry;
    }

    public record GraphWithRecords(ProvenanceGraph graph, Map<UUID, ProvenanceRecord> records) {}

    public Optional<ProvenanceGraph> buildGraph(UUID rootId) {
        return buildGraphWithRecords(rootId).map(GraphWithRecords::graph);
    }

    public Optional<GraphWithRecords> buildGraphWithRecords(UUID rootId) {
        Set<UUID> visited = new HashSet<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        List<UUID> missingPredecessors = new ArrayList<>();
        Map<UUID, ProvenanceRecord> loadedRecords = new LinkedHashMap<>();

        Set<UUID> currentLevel = new LinkedHashSet<>();
        currentLevel.add(rootId);
        int depth = 0;
        int actualMaxDepth = 0;

        while (!currentLevel.isEmpty()) {
            Map<UUID, ProvenanceRecord> levelRecords = provenanceRegistry.findAllByIds(currentLevel);
            Set<UUID> nextLevel = new LinkedHashSet<>();

            for (UUID id : currentLevel) {
                if (!visited.add(id)) {
                    continue;
                }
                ProvenanceRecord record = levelRecords.get(id);
                if (record == null) {
                    if (depth == 0) {
                        log.warn("Root provenance record not found: {}", rootId);
                        return Optional.empty();
                    }
                    log.warn("Predecessor record not found: {}", id);
                    missingPredecessors.add(id);
                    continue;
                }

                loadedRecords.put(id, record);
                actualMaxDepth = Math.max(actualMaxDepth, depth);

                List<Predecessor> predecessors = record.metadata().predecessors();
                if (predecessors == null) {
                    predecessors = Collections.emptyList();
                }

                String signerIdentity = record.signature().details() != null
                    ? record.signature().details().signerIdentity()
                    : null;

                nodes.add(new GraphNode(
                    id,
                    record.metadata().dataId(),
                    record.metadata().dataType(),
                    record.signature().signingTime(),
                    depth,
                    predecessors.size(),
                    signerIdentity
                ));

                for (Predecessor predecessor : predecessors) {
                    UUID predecessorId = predecessor.id();
                    edges.add(new GraphEdge(id, predecessorId));
                    if (!visited.contains(predecessorId)) {
                        nextLevel.add(predecessorId);
                    }
                }
            }

            currentLevel = nextLevel;
            depth++;
        }

        GraphMetadata metadata = new GraphMetadata(
            nodes.size(),
            actualMaxDepth,
            missingPredecessors
        );

        ProvenanceGraph graph = new ProvenanceGraph(rootId, nodes, edges, metadata);
        log.info("Built provenance graph for root {} with {} nodes and {} edges",
            rootId, nodes.size(), edges.size());

        return Optional.of(new GraphWithRecords(graph, loadedRecords));
    }
}
