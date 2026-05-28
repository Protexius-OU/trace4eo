import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as d3 from 'd3'
import type { ProvenanceGraph, GraphNode } from '../types/provenance'
import { meaningfulPrefix, stripPrefix, formatDate, sortTypesByMinDepth } from '../utils/labelUtils'
import './ProvenanceChainList.css'

interface Props {
  graph: ProvenanceGraph
}

interface NodeRow {
  kind: 'node'
  node: GraphNode
  depth: number
  parentBars: boolean[]
  isLast: boolean
  hasChildren: boolean
}

interface ShowMoreRow {
  kind: 'show-more'
  pathKey: string
  hidden: number
  depth: number
  parentBars: boolean[]
}

type DisplayRow = NodeRow | ShowMoreRow

const INITIAL_LIMIT = 5
const SHOW_MORE_STEP = 10

function nodeHeadRail(parentBars: boolean[], depth: number, isLast: boolean): string {
  let s = ''
  for (const bar of parentBars) s += bar ? '│  ' : '   '
  if (depth > 0) s += isLast ? '└──' : '├──'
  s += '●'
  return s
}

function nodeContRail(parentBars: boolean[], depth: number, isLast: boolean, hasChildren: boolean): string {
  let s = ''
  for (const bar of parentBars) s += bar ? '│  ' : '   '
  if (depth > 0) s += isLast ? '   ' : '│  '
  s += hasChildren ? '│' : ' '
  return s
}

function showMoreRail(parentBars: boolean[]): string {
  let s = ''
  for (const bar of parentBars) s += bar ? '│  ' : '   '
  s += '└─ '
  return s
}

export default function ProvenanceChainList({ graph }: Props) {
  const navigate = useNavigate()
  const [hoveredDupId, setHoveredDupId] = useState<string | null>(null)
  const [limits, setLimits] = useState<Map<string, number>>(() => new Map())

  function showMore(pathKey: string) {
    setLimits(prev => {
      const next = new Map(prev)
      const current = next.get(pathKey) ?? INITIAL_LIMIT
      next.set(pathKey, current + SHOW_MORE_STEP)
      return next
    })
  }

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

  const rows = useMemo((): DisplayRow[] => {
    const out: DisplayRow[] = []

    function visit(id: string, depth: number, isLast: boolean, parentBars: boolean[], ancestors: ReadonlySet<string>, parentPath: string) {
      if (ancestors.has(id)) return
      const node = nodeById.get(id)
      if (!node) return

      const preds = (predecessorMap.get(id) ?? []).filter(pid => !ancestors.has(pid))
      out.push({ kind: 'node', node, depth, parentBars, isLast, hasChildren: preds.length > 0 })

      if (preds.length === 0) return

      const pathKey = parentPath === '' ? id : `${parentPath}|${id}`
      const limit = Math.min(limits.get(pathKey) ?? INITIAL_LIMIT, preds.length)
      const hidden = preds.length - limit

      const childAncestors = new Set(ancestors)
      childAncestors.add(id)
      const childParentBars = [...parentBars, !isLast]

      for (let i = 0; i < limit; i++) {
        const isLastChild = i === limit - 1 && hidden === 0
        visit(preds[i]!, depth + 1, isLastChild, childParentBars, childAncestors, pathKey)
      }

      if (hidden > 0) {
        out.push({
          kind: 'show-more',
          pathKey,
          hidden,
          depth: depth + 1,
          parentBars: childParentBars,
        })
      }
    }

    visit(graph.rootId, 0, true, [], new Set(), '')
    return out
  }, [graph.rootId, predecessorMap, nodeById, limits])

  const duplicateIds = useMemo(() => {
    const counts = new Map<string, number>()
    for (const r of rows) {
      if (r.kind !== 'node') continue
      counts.set(r.node.id, (counts.get(r.node.id) ?? 0) + 1)
    }
    const dups = new Set<string>()
    for (const [id, count] of counts) if (count > 1) dups.add(id)
    return dups
  }, [rows])

  return (
    <div className="chain-list-wrapper">
      <div className="chain-list">
        {rows.map((row, idx) => {
          if (row.kind === 'show-more') {
            const remaining = Math.min(SHOW_MORE_STEP, row.hidden)
            return (
              <div key={`sm-${row.pathKey}-${idx}`} className="chain-row chain-row-show-more">
                <pre className="chain-rail" aria-hidden="true">{showMoreRail(row.parentBars)}</pre>
                <button
                  type="button"
                  className="chain-show-more-btn"
                  onClick={() => showMore(row.pathKey)}
                >
                  Show {remaining} more
                </button>
              </div>
            )
          }

          const isViewing = row.node.id === graph.rootId
          const headRail = nodeHeadRail(row.parentBars, row.depth, row.isLast)
          const contRail = nodeContRail(row.parentBars, row.depth, row.isLast, row.hasChildren)
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
              onFocus={() => {
                if (isDuplicate) setHoveredDupId(row.node.id)
              }}
              onBlur={() => {
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
                <span style={{ color }}>{headRail}</span>
                {'\n'}
                {contRail}
                {'\n'}
                {contRail}
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
