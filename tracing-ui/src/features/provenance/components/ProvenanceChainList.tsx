import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as d3 from 'd3'
import type { ProvenanceGraph, GraphNode } from '../types/provenance'
import { meaningfulPrefix, stripPrefix, formatDate, sortTypesByMinDepth } from '../utils/labelUtils'
import './ProvenanceChainList.css'

interface Props {
  graph: ProvenanceGraph
}

interface RowInfo {
  node: GraphNode
  depth: number
  parentBars: boolean[]
  isLast: boolean
  hasChildren: boolean
}


export default function ProvenanceChainList({ graph }: Props) {
  const navigate = useNavigate()
  const [hoveredDupId, setHoveredDupId] = useState<string | null>(null)

  const sortedTypes = useMemo(() => sortTypesByMinDepth(graph.nodes), [graph.nodes])

  const typeColors = useMemo(() => {
    const scale = d3.scaleOrdinal<string>(d3.schemeObservable10)
    for (const t of sortedTypes) scale(t)
    return scale
  }, [sortedTypes])

  const labelPrefix = useMemo(() => {
    const tokens: string[] = []
    for (const n of graph.nodes) {
      if (n.dataId) tokens.push(n.dataId)
      if (n.dataType) tokens.push(n.dataType)
    }
    return meaningfulPrefix(tokens)
  }, [graph.nodes])

  const nodeById = useMemo(() => {
    const m = new Map<string, GraphNode>()
    for (const n of graph.nodes) m.set(n.id, n)
    return m
  }, [graph.nodes])

  // For each node, list its predecessors. Edges are stored as
  // (source = consumer, target = predecessor), so the predecessors of `id`
  // are the target nodes of edges where source = id.
  const predecessorMap = useMemo(() => {
    const m = new Map<string, string[]>()
    for (const e of graph.edges) {
      const list = m.get(e.sourceId) ?? []
      list.push(e.targetId)
      m.set(e.sourceId, list)
    }
    return m
  }, [graph.edges])

  const rows = useMemo((): RowInfo[] => {
    const out: RowInfo[] = []

    function visit(id: string, depth: number, isLast: boolean, parentBars: boolean[], ancestors: ReadonlySet<string>) {
      if (ancestors.has(id)) return
      const node = nodeById.get(id)
      if (!node) return

      const preds = (predecessorMap.get(id) ?? []).filter(pid => !ancestors.has(pid))
      out.push({ node, depth, parentBars, isLast, hasChildren: preds.length > 0 })

      const childAncestors = new Set(ancestors)
      childAncestors.add(id)

      for (let i = 0; i < preds.length; i++) {
        const isLastChild = i === preds.length - 1
        const childParentBars = [...parentBars, !isLast]
        visit(preds[i]!, depth + 1, isLastChild, childParentBars, childAncestors)
      }
    }

    visit(graph.rootId, 0, true, [], new Set())
    return out
  }, [graph.rootId, predecessorMap, nodeById])

  const duplicateIds = useMemo(() => {
    const counts = new Map<string, number>()
    for (const r of rows) counts.set(r.node.id, (counts.get(r.node.id) ?? 0) + 1)
    const dups = new Set<string>()
    for (const [id, count] of counts) if (count > 1) dups.add(id)
    return dups
  }, [rows])

  function buildRail(info: RowInfo, kind: 'head' | 'cont'): string {
    let s = ''
    for (const bar of info.parentBars) {
      s += bar ? '│  ' : '   '
    }
    if (kind === 'head') {
      if (info.depth > 0) {
        s += info.isLast ? '└──' : '├──'
      }
      s += '●'
    } else {
      if (info.depth > 0) {
        s += info.isLast ? '   ' : '│  '
      }
      s += info.hasChildren ? '│' : ' '
    }
    return s
  }

  return (
    <div className="chain-list-wrapper">
      <div className="chain-list">
        {rows.map((row, idx) => {
          const isViewing = row.node.id === graph.rootId
          const headRail = buildRail(row, 'head')
          const contRail = buildRail(row, 'cont')
          const color = typeColors(row.node.dataType)
          const date = formatDate(row.node.signingTime)
          const strippedDataId = stripPrefix(row.node.dataId, labelPrefix)
          const strippedType = stripPrefix(row.node.dataType, labelPrefix)

          const isDuplicate = duplicateIds.has(row.node.id)
          const isHighlighted = isDuplicate && hoveredDupId === row.node.id
          return (
            <div
              key={`${row.node.id}-${idx}`}
              className={
                'chain-row' +
                (isViewing ? ' chain-row-viewing' : '') +
                (isDuplicate ? ' chain-row-duplicate' : '') +
                (isHighlighted ? ' chain-row-highlighted' : '')
              }
              onClick={() => {
                if (!isViewing) navigate(`/records/${row.node.id}/graph`)
              }}
              onMouseEnter={() => {
                if (isDuplicate) setHoveredDupId(row.node.id)
              }}
              onMouseLeave={() => {
                if (isDuplicate) setHoveredDupId(null)
              }}
              role={isViewing ? undefined : 'button'}
              tabIndex={isViewing ? undefined : 0}
              onKeyDown={(e) => {
                if (!isViewing && (e.key === 'Enter' || e.key === ' ')) {
                  e.preventDefault()
                  navigate(`/records/${row.node.id}/graph`)
                }
              }}
            >
              <pre className="chain-rail" aria-hidden="true">
                <span className="chain-rail-line chain-rail-head" style={{ color }}>{headRail}</span>
                {'\n'}
                <span className="chain-rail-line">{contRail}</span>
                {'\n'}
                <span className="chain-rail-line">{contRail}</span>
              </pre>
              <div className="chain-content">
                <div className="chain-title-row">
                  <span className="chain-type" style={{ color }}>
                    {strippedType || row.node.dataType || 'Unknown'}
                  </span>
                  {isViewing && <span className="chain-viewing-badge">VIEWING</span>}
                </div>
                <div className="chain-data-id">{strippedDataId || row.node.dataId || 'N/A'}</div>
                <div className="chain-meta">
                  {date && <span>{date}</span>}
                  {date && row.node.signerIdentity && <span className="chain-sep">·</span>}
                  {row.node.signerIdentity && <span>{row.node.signerIdentity}</span>}
                  {row.node.predecessorCount > 0 && (
                    <>
                      <span className="chain-sep">·</span>
                      <span>
                        {row.node.predecessorCount} predecessor
                        {row.node.predecessorCount === 1 ? '' : 's'}
                      </span>
                    </>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
