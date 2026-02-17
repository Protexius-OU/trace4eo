import type { ProvenanceGraph, GraphEdge, DisplayNode, GroupNode } from '../types/provenance'

const COLLAPSE_THRESHOLD = 5

interface DisplayGraph {
  nodes: DisplayNode[]
  edges: GraphEdge[]
}

export function buildDisplayGraph(
  graph: ProvenanceGraph,
  expandedGroups: Set<string>
): DisplayGraph {
  const nodeMap = new Map(graph.nodes.map(n => [n.id, n]))

  // Build predecessor map: sourceId (child) -> list of targetIds (predecessors)
  // Edges go from child (sourceId) to predecessor (targetId)
  const predecessorMap = new Map<string, string[]>()
  for (const edge of graph.edges) {
    const preds = predecessorMap.get(edge.sourceId)
    if (preds) {
      preds.push(edge.targetId)
    } else {
      predecessorMap.set(edge.sourceId, [edge.targetId])
    }
  }

  const hiddenNodeIds = new Set<string>()
  const groupNodes: GroupNode[] = []
  const groupEdges: GraphEdge[] = []
  const removedEdges = new Set<string>()

  for (const [childId, predIds] of predecessorMap) {
    // Group predecessors by dataType
    const byType = new Map<string, string[]>()
    for (const predId of predIds) {
      const dataType = nodeMap.get(predId)?.dataType ?? ''
      const list = byType.get(dataType)
      if (list) {
        list.push(predId)
      } else {
        byType.set(dataType, [predId])
      }
    }

    const childNode = nodeMap.get(childId)
    const childDepth = childNode ? childNode.depth : 0

    for (const [dataType, typeIds] of byType) {
      if (typeIds.length <= COLLAPSE_THRESHOLD) continue

      const groupId = `group::${childId}::${dataType}`
      if (expandedGroups.has(groupId)) continue

      for (const predId of typeIds) {
        hiddenNodeIds.add(predId)
        removedEdges.add(`${childId}->${predId}`)
      }

      groupNodes.push({
        id: groupId,
        parentNodeId: childId,
        hiddenNodeIds: typeIds,
        dataType,
        depth: childDepth + 1,
        count: typeIds.length,
        isGroup: true,
      })

      groupEdges.push({ sourceId: childId, targetId: groupId })
    }
  }

  // A node should only be hidden if it's not also visible through another path.
  const actuallyHidden = new Set<string>()
  for (const nodeId of hiddenNodeIds) {
    // Check if all edges pointing TO this node (where it's the target/predecessor) are removed
    const allIncomingEdgesRemoved = graph.edges
      .filter(e => e.targetId === nodeId)
      .every(e => removedEdges.has(`${e.sourceId}->${e.targetId}`))

    // Check if this node is itself a source of edges (has its own predecessors)
    // If so, it's part of a sub-tree and should stay visible
    const hasOwnPredecessors = graph.edges.some(e => e.sourceId === nodeId)

    if (allIncomingEdgesRemoved && !hasOwnPredecessors) {
      actuallyHidden.add(nodeId)
    }
  }

  const filteredNodes: DisplayNode[] = graph.nodes
    .filter(n => !actuallyHidden.has(n.id))
    .map(n => ({ ...n, isGroup: false as const }))

  const filteredEdges = graph.edges.filter(
    e => !removedEdges.has(`${e.sourceId}->${e.targetId}`)
  )

  return {
    nodes: [...filteredNodes, ...groupNodes],
    edges: [...filteredEdges, ...groupEdges],
  }
}
