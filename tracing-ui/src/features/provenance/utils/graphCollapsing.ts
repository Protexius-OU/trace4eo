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

  type Candidate = {
    childId: string
    childDepth: number
    dataType: string
    sortedIds: string[]
  }
  const candidates: Candidate[] = []

  for (const [childId, predIds] of predecessorMap) {
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
      candidates.push({
        childId,
        childDepth,
        dataType,
        sortedIds: [...typeIds].sort(),
      })
    }
  }

  // Merge candidates with identical predecessor sets so multiple parents share one collapsed node.
  type Merged = {
    dataType: string
    hiddenIds: string[]
    childIds: string[]
    maxParentDepth: number
  }
  const byContent = new Map<string, Merged>()
  for (const c of candidates) {
    const key = `${c.dataType}::${c.sortedIds.join(',')}`
    const existing = byContent.get(key)
    if (existing) {
      existing.childIds.push(c.childId)
      existing.maxParentDepth = Math.max(existing.maxParentDepth, c.childDepth)
    } else {
      byContent.set(key, {
        dataType: c.dataType,
        hiddenIds: c.sortedIds,
        childIds: [c.childId],
        maxParentDepth: c.childDepth,
      })
    }
  }

  const hiddenNodeIds = new Set<string>()
  const groupNodes: GroupNode[] = []
  const groupEdges: GraphEdge[] = []
  const removedEdges = new Set<string>()

  for (const merged of byContent.values()) {
    const sortedChildIds = [...merged.childIds].sort()
    const groupId = `group::${sortedChildIds.join(',')}::${merged.dataType}`

    if (expandedGroups.has(groupId)) continue

    for (const predId of merged.hiddenIds) {
      hiddenNodeIds.add(predId)
    }
    for (const childId of merged.childIds) {
      for (const predId of merged.hiddenIds) {
        removedEdges.add(`${childId}->${predId}`)
      }
    }

    groupNodes.push({
      id: groupId,
      parentNodeId: sortedChildIds[0]!,
      hiddenNodeIds: merged.hiddenIds,
      dataType: merged.dataType,
      depth: merged.maxParentDepth + 1,
      count: merged.hiddenIds.length,
      isGroup: true,
    })

    for (const childId of merged.childIds) {
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
