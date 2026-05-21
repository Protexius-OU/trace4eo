import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as d3 from 'd3'
import type { ProvenanceGraph, GraphNode } from '../types/provenance'
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

function commonPrefix(strings: string[]): string {
  if (strings.length < 2) return ''
  let prefix = strings[0] ?? ''
  for (let i = 1; i < strings.length; i++) {
    const s = strings[i] ?? ''
    while (!s.startsWith(prefix)) {
      prefix = prefix.slice(0, -1)
      if (prefix === '') return ''
    }
  }
  return prefix
}

function meaningfulPrefix(strings: string[]): string {
  const filtered = strings.filter((s): s is string => Boolean(s))
  if (filtered.length < 2) return ''
  const cp = commonPrefix(filtered)
  if (filtered.some(s => s === cp)) return ''
  const m = cp.match(/^(.+[-_./:])/)
  const trimmed = m?.[1] ?? ''
  return trimmed.length >= 4 ? trimmed : ''
}

function stripPrefix(str: string | null | undefined, prefix: string): string {
  if (!str) return ''
  return prefix && str.startsWith(prefix) ? str.slice(prefix.length) : str
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

export default function ProvenanceChainList({ graph }: Props) {
  const navigate = useNavigate()
  const [hoveredDupId, setHoveredDupId] = useState<string | null>(null)

  // Sort types by their minimum depth so colors are assigned in the same
  // order as the graph view (which seeds its ordinal scale via the legend).
  const sortedTypes = useMemo(() => {
    const typeMinDepth = new Map<string, number>()
    for (const node of graph.nodes) {
      const current = typeMinDepth.get(node.dataType)
      if (current === undefined || node.depth < current) {
        typeMinDepth.set(node.dataType, node.depth)
      }
    }
    return [...typeMinDepth.entries()]
      .sort((a, b) => a[1] - b[1])
      .map(([type]) => type)
  }, [graph.nodes])

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
