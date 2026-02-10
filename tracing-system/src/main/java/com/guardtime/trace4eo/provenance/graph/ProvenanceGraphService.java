package com.guardtime.trace4eo.provenance.graph;

import com.guardtime.trace4eo.provenance.ProvenanceRegistry;
import com.guardtime.trace4eo.provenance.record.Predecessor;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
        Optional<ProvenanceRecord> rootRecord = provenanceRegistry.get(rootId);
        if (rootRecord.isEmpty()) {
            log.warn("Root provenance record not found: {}", rootId);
            return Optional.empty();
        }

        Set<UUID> visited = new HashSet<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        List<UUID> missingPredecessors = new ArrayList<>();
        Map<UUID, ProvenanceRecord> loadedRecords = new LinkedHashMap<>();

        Queue<TraversalItem> queue = new LinkedList<>();
        queue.add(new TraversalItem(rootId, 0));

        int actualMaxDepth = 0;

        while (!queue.isEmpty()) {
            TraversalItem item = queue.poll();

            if (visited.contains(item.id())) {
                continue;
            }

            visited.add(item.id());
            actualMaxDepth = Math.max(actualMaxDepth, item.depth());

            Optional<ProvenanceRecord> optionalRecord = provenanceRegistry.get(item.id());
            if (optionalRecord.isEmpty()) {
                log.warn("Predecessor record not found: {}", item.id());
                missingPredecessors.add(item.id());
                continue;
            }

            ProvenanceRecord record = optionalRecord.get();
            loadedRecords.put(record.id(), record);
            List<Predecessor> predecessors = record.metadata().predecessors();
            if (predecessors == null) {
                predecessors = Collections.emptyList();
            }

            String signerIdentity = null;
            if (record.signature().details() != null) {
                signerIdentity = record.signature().details().signerIdentity();
            }

            GraphNode node = new GraphNode(
                record.id(),
                record.metadata().dataId(),
                record.metadata().dataType(),
                record.signature().signingTime(),
                item.depth(),
                predecessors.size(),
                signerIdentity
            );
            nodes.add(node);

            for (Predecessor predecessor : predecessors) {
                UUID predecessorId = predecessor.id();
                edges.add(new GraphEdge(record.id(), predecessorId));
                if (!visited.contains(predecessorId)) {
                    queue.add(new TraversalItem(predecessorId, item.depth() + 1));
                }
            }
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

    private record TraversalItem(UUID id, int depth) {
    }
}
