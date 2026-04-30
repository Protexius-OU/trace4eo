package com.protexius.trace4eo.provenance.graph;

import com.protexius.trace4eo.provenance.HashAlgorithm;
import com.protexius.trace4eo.provenance.ProvenanceRegistry;
import com.protexius.trace4eo.provenance.ProvenanceRecordImpl;
import com.protexius.trace4eo.provenance.ProvenanceSignature;
import com.protexius.trace4eo.provenance.record.Metadata;
import com.protexius.trace4eo.provenance.record.Predecessor;
import com.protexius.trace4eo.provenance.record.ProvenanceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvenanceGraphServiceTest {

    @Mock
    private ProvenanceRegistry provenanceRegistry;

    private ProvenanceGraphService graphService;

    @BeforeEach
    void setUp() {
        graphService = new ProvenanceGraphService(provenanceRegistry);
    }

    @Test
    void buildGraphReturnsEmptyWhenRootNotFound() {
        UUID rootId = UUID.randomUUID();
        when(provenanceRegistry.get(rootId)).thenReturn(Optional.empty());

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isEmpty());
    }

    @Test
    void buildGraphReturnsSingleNodeGraphWhenNoPredecessors() {
        UUID rootId = UUID.randomUUID();
        ProvenanceRecord rootRecord = createRecord(rootId, "TypeA", Collections.emptyList());
        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(rootId, graph.rootId());
        assertEquals(1, graph.nodes().size());
        assertEquals(0, graph.edges().size());
        assertEquals(1, graph.metadata().totalNodes());
        assertEquals(0, graph.metadata().maxDepth());
    }

    @Test
    void buildGraphIncludesPredecessors() {
        UUID rootId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();

        ProvenanceRecord predecessorRecord = createRecord(predecessorId, "TypeB", Collections.emptyList());
        ProvenanceRecord rootRecord = createRecord(rootId, "TypeA", List.of(new Predecessor(predecessorId)));

        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));
        when(provenanceRegistry.get(predecessorId)).thenReturn(Optional.of(predecessorRecord));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals(2, graph.metadata().totalNodes());
        assertEquals(1, graph.metadata().maxDepth());

        GraphEdge edge = graph.edges().getFirst();
        assertEquals(rootId, edge.sourceId());
        assertEquals(predecessorId, edge.targetId());
    }

    @Test
    void buildGraphHandlesMissingPredecessors() {
        UUID rootId = UUID.randomUUID();
        UUID missingPredecessorId = UUID.randomUUID();

        ProvenanceRecord rootRecord = createRecord(rootId, "TypeA", List.of(new Predecessor(missingPredecessorId)));

        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));
        when(provenanceRegistry.get(missingPredecessorId)).thenReturn(Optional.empty());

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(1, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals(1, graph.metadata().missingPredecessors().size());
        assertEquals(missingPredecessorId, graph.metadata().missingPredecessors().getFirst());
    }

    @Test
    void buildGraphHandlesNullPredecessorsList() {
        UUID rootId = UUID.randomUUID();
        ProvenanceRecord rootRecord = createRecordWithNullPredecessors(rootId, "TypeA");
        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(1, graph.nodes().size());
        assertEquals(0, graph.edges().size());
    }

    @Test
    void buildGraphHandlesCyclicReferences() {
        UUID rootId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();

        ProvenanceRecord predecessorRecord = createRecord(predecessorId, "TypeB", List.of(new Predecessor(rootId)));
        ProvenanceRecord rootRecord = createRecord(rootId, "TypeA", List.of(new Predecessor(predecessorId)));

        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));
        when(provenanceRegistry.get(predecessorId)).thenReturn(Optional.of(predecessorRecord));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(2, graph.nodes().size());
        assertEquals(2, graph.edges().size());
    }

    @Test
    void buildGraphHandlesMultiplePredecessors() {
        UUID rootId = UUID.randomUUID();
        UUID pred1Id = UUID.randomUUID();
        UUID pred2Id = UUID.randomUUID();
        UUID pred3Id = UUID.randomUUID();

        ProvenanceRecord pred1 = createRecord(pred1Id, "TypeB", Collections.emptyList());
        ProvenanceRecord pred2 = createRecord(pred2Id, "TypeB", Collections.emptyList());
        ProvenanceRecord pred3 = createRecord(pred3Id, "TypeB", Collections.emptyList());
        ProvenanceRecord rootRecord = createRecord(rootId, "TypeA", List.of(
            new Predecessor(pred1Id),
            new Predecessor(pred2Id),
            new Predecessor(pred3Id)
        ));

        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));
        when(provenanceRegistry.get(pred1Id)).thenReturn(Optional.of(pred1));
        when(provenanceRegistry.get(pred2Id)).thenReturn(Optional.of(pred2));
        when(provenanceRegistry.get(pred3Id)).thenReturn(Optional.of(pred3));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        ProvenanceGraph graph = result.get();
        assertEquals(4, graph.nodes().size());
        assertEquals(3, graph.edges().size());
        assertEquals(1, graph.metadata().maxDepth());
    }

    @Test
    void buildGraphNodeContainsCorrectMetadata() {
        UUID rootId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        Instant signingTime = Instant.parse("2024-01-15T10:30:00Z");

        ProvenanceRecord predecessorRecord = createRecord(predecessorId, "TypeB", Collections.emptyList());
        ProvenanceRecord rootRecord = createRecordWithSigningTime(rootId, "TypeA", "data-123",
            List.of(new Predecessor(predecessorId)), signingTime);

        when(provenanceRegistry.get(rootId)).thenReturn(Optional.of(rootRecord));
        when(provenanceRegistry.get(predecessorId)).thenReturn(Optional.of(predecessorRecord));

        Optional<ProvenanceGraph> result = graphService.buildGraph(rootId);

        assertTrue(result.isPresent());
        GraphNode rootNode = result.get().nodes().stream()
            .filter(n -> n.id().equals(rootId))
            .findFirst()
            .orElseThrow();

        assertEquals(rootId, rootNode.id());
        assertEquals("data-123", rootNode.dataId());
        assertEquals("TypeA", rootNode.dataType());
        assertEquals(signingTime, rootNode.signingTime());
        assertEquals(0, rootNode.depth());
        assertEquals(1, rootNode.predecessorCount());
    }

    private ProvenanceRecord createRecord(UUID id, String dataType, List<Predecessor> predecessors) {
        return createRecordWithSigningTime(id, dataType, "data-" + id, predecessors, Instant.now());
    }

    private ProvenanceRecord createRecordWithSigningTime(UUID id, String dataType, String dataId,
                                                          List<Predecessor> predecessors, Instant signingTime) {
        Metadata metadata = new Metadata(dataId, dataType, predecessors);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1, 2, 3}, signingTime, HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, null, null, signature, null);
    }

    private ProvenanceRecord createRecordWithNullPredecessors(UUID id, String dataType) {
        Metadata metadata = new Metadata("data-" + id, dataType, null);
        ProvenanceSignature signature = new ProvenanceSignature(new byte[]{1, 2, 3}, Instant.now(), HashAlgorithm.SHA256);
        return new ProvenanceRecordImpl(id, metadata, null, null, signature, null);
    }
}
