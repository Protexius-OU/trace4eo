import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import './ProvenanceGraphViewer.css'
import * as d3 from 'd3'
import type { ProvenanceGraph, GraphNode, DisplayNode, GroupNode } from '../types/provenance'
import { buildDisplayGraph } from '../utils/graphCollapsing'
import PredecessorListModal from './PredecessorListModal'

interface Props {
  graph: ProvenanceGraph
}

type SimNode = DisplayNode & {
  x?: number
  y?: number
  fx?: number | null
  fy?: number | null
}

interface SimLink {
  source: SimNode | string
  target: SimNode | string
}

const BOX_WIDTH = 180
const BOX_HEIGHT = 90
const LABEL_MAX_LEN = 28
const DEPTH_SPACING = 280
const LINK_DISTANCE = 320
const COLLIDE_RADIUS = BOX_WIDTH / 2 + 30

function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

function truncate(str: string | null | undefined, maxLen: number): string {
  if (!str) return ''
  return str.length > maxLen ? str.substring(0, maxLen - 1) + '…' : str
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

function NodeDetails({ node }: { node: GraphNode }) {
  return (
    <>
      <div style={{ fontWeight: 600, marginBottom: '4px', paddingRight: '20px' }}>{node.dataType || 'Unknown'}</div>
      <div><span style={{ color: '#666' }}>ID:</span> <span style={{ fontFamily: 'monospace', fontSize: '10px' }}>{node.id}</span></div>
      <div><span style={{ color: '#666' }}>Data ID:</span> {node.dataId || 'N/A'}</div>
      <div><span style={{ color: '#666' }}>Signed:</span> {node.signingTime ? new Date(node.signingTime).toLocaleString() : 'N/A'}</div>
      <div><span style={{ color: '#666' }}>Signed by:</span> {node.signerIdentity || '—'}</div>
      <div><span style={{ color: '#666' }}>Predecessors:</span> {node.predecessorCount}</div>
    </>
  )
}

export default function ProvenanceGraphViewer({ graph }: Props) {
  const svgRef = useRef<SVGSVGElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const [tooltip, setTooltip] = useState<{ node: DisplayNode; x: number; y: number } | null>(null)
  const [expandedGroups] = useState<Set<string>>(() => new Set())
  const [selectedGroup, setSelectedGroup] = useState<GroupNode | null>(null)

  // Reset selected group when graph changes
  useEffect(() => {
    setSelectedGroup(null)
  }, [graph])

  const displayGraph = useMemo(
    () => buildDisplayGraph(graph, expandedGroups),
    [graph, expandedGroups]
  )

  const typeColors = useMemo(() => d3.scaleOrdinal(d3.schemeObservable10), [])

  const labelPrefix = useMemo(() => {
    const tokens: string[] = []
    for (const n of graph.nodes) {
      if (n.dataId) tokens.push(n.dataId)
      if (n.dataType) tokens.push(n.dataType)
    }
    return meaningfulPrefix(tokens)
  }, [graph.nodes])

  const rootId = graph.rootId

  useEffect(() => {
    if (!svgRef.current || !containerRef.current) return

    const svg = d3.select(svgRef.current)
    svg.selectAll('*').remove()

    const width = containerRef.current.clientWidth
    const height = containerRef.current.clientHeight || 600

    svg.attr('viewBox', `0 0 ${width} ${height}`)

    const links: SimLink[] = displayGraph.edges.map(e => ({
      source: e.sourceId,
      target: e.targetId
    }))

    const nodes: SimNode[] = displayGraph.nodes.map(n => ({ ...n }))

    const g = svg.append('g')

    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 4])
      .on('zoom', (event) => {
        g.attr('transform', event.transform)
      })

    svg.call(zoom)

    const link = g.append('g')
      .attr('class', 'links')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#9ca3af')
      .attr('stroke-opacity', 0.8)
      .attr('stroke-width', 2)

    const node = g.append('g')
      .attr('class', 'nodes')
      .selectAll<SVGGElement, SimNode>('g')
      .data(nodes)
      .join('g')

    node.each(function(d) {
      const el = d3.select(this)

      if (d.isGroup) {
        // Group node rendering — same box style, colored by type
        const color = typeColors(d.dataType)
        const strokeColor = d3.color(color)?.darker(0.5)?.toString() ?? color

        el.append('rect')
          .attr('x', -BOX_WIDTH / 2)
          .attr('y', -BOX_HEIGHT / 2)
          .attr('width', BOX_WIDTH)
          .attr('height', BOX_HEIGHT)
          .attr('rx', 6)
          .attr('ry', 6)
          .attr('fill', color)
          .attr('stroke', strokeColor)
          .attr('stroke-width', 2)
          .style('cursor', 'pointer')

        const fo = el.append('foreignObject')
          .attr('x', -BOX_WIDTH / 2 + 8)
          .attr('y', -BOX_HEIGHT / 2 + 6)
          .attr('width', BOX_WIDTH - 16)
          .attr('height', BOX_HEIGHT - 12)
          .style('pointer-events', 'none')

        const div = fo.append('xhtml:div')
          .style('font-size', '11px')
          .style('line-height', '1.3')
          .style('color', '#fff')
          .style('text-shadow', '0 1px 2px rgba(0,0,0,0.3)')

        div.append('xhtml:div')
          .style('font-weight', '600')
          .text(`${d.count} records`)

        div.append('xhtml:div')
          .style('font-size', '10px')
          .style('opacity', '0.9')
          .style('margin-top', '2px')
          .text('Click to view')
      } else {
        // Regular node rendering
        const color = typeColors(d.dataType)
        const strokeColor = d3.color(color)?.darker(0.5)?.toString() ?? color
        const isRoot = d.id === rootId

        el.append('rect')
          .attr('x', -BOX_WIDTH / 2)
          .attr('y', -BOX_HEIGHT / 2)
          .attr('width', BOX_WIDTH)
          .attr('height', BOX_HEIGHT)
          .attr('rx', 6)
          .attr('ry', 6)
          .attr('fill', color)
          .attr('stroke', isRoot ? '#1a1a2e' : strokeColor)
          .attr('stroke-width', isRoot ? 3 : 2)
          .style('cursor', isRoot ? 'default' : 'pointer')

        const fo = el.append('foreignObject')
          .attr('x', -BOX_WIDTH / 2 + 10)
          .attr('y', -BOX_HEIGHT / 2 + 8)
          .attr('width', BOX_WIDTH - 20)
          .attr('height', BOX_HEIGHT - 16)
          .style('pointer-events', 'none')

        const div = fo.append('xhtml:div')
          .style('font-size', '13px')
          .style('line-height', '1.3')
          .style('color', '#fff')
          .style('text-shadow', '0 1px 2px rgba(0,0,0,0.3)')

        const primaryLabel = truncate(stripPrefix(d.dataId, labelPrefix), LABEL_MAX_LEN) || 'N/A'
        const dateLabel = formatDate(d.signingTime)

        div.append('xhtml:div')
          .style('font-weight', '600')
          .style('white-space', 'nowrap')
          .style('overflow', 'hidden')
          .style('text-overflow', 'ellipsis')
          .text(primaryLabel)

        if (dateLabel) {
          div.append('xhtml:div')
            .style('font-size', '11px')
            .style('opacity', '0.9')
            .style('margin-top', '4px')
            .style('white-space', 'nowrap')
            .style('overflow', 'hidden')
            .style('text-overflow', 'ellipsis')
            .text(dateLabel)
        }
      }
    })

    node.on('mouseenter', function(event, d) {
      d.fx = d.x
      d.fy = d.y
      setTooltip({ node: d, x: event.clientX + 15, y: event.clientY + 15 })
    })
    .on('mouseleave', (_event, d) => {
      d.fx = null
      d.fy = null
      setTooltip(null)
    })
    .on('click', (_event, d) => {
      if (d.isGroup) {
        setSelectedGroup(d)
      } else if (d.id !== rootId) {
        navigate(`/records/${d.id}/graph`)
      }
    })

    const maxDepth = Math.max(...nodes.map(n => n.depth), 0)
    const graphWidth = maxDepth * DEPTH_SPACING
    const offsetX = (width + graphWidth) / 2

    nodes.forEach(n => {
      n.x = offsetX - n.depth * DEPTH_SPACING
      n.y = height / 2
    })

    const applyPositions = () => {
      link
        .attr('x1', d => (d.source as SimNode).x ?? 0)
        .attr('y1', d => (d.source as SimNode).y ?? 0)
        .attr('x2', d => (d.target as SimNode).x ?? 0)
        .attr('y2', d => (d.target as SimNode).y ?? 0)

      node.attr('transform', d => `translate(${d.x ?? 0},${d.y ?? 0})`)
    }

    const simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink<SimNode, SimLink>(links).id(d => d.id).distance(LINK_DISTANCE))
      .force('charge', d3.forceManyBody().strength(-600))
      .force('x', d3.forceX<SimNode>().x(d => offsetX - d.depth * DEPTH_SPACING).strength(0.6))
      .force('y', d3.forceY<SimNode>().y(height / 2).strength(0.05))
      .force('collide', d3.forceCollide().radius(COLLIDE_RADIUS))
      .stop()

    const ticks = Math.ceil(Math.log(simulation.alphaMin()) / Math.log(1 - simulation.alphaDecay()))
    simulation.tick(ticks)
    applyPositions()
    simulation.on('tick', applyPositions)

    const drag = d3.drag<SVGGElement, SimNode>()
      .on('start', (event) => {
        if (!event.active) simulation.alphaTarget(0.3).restart()
        event.subject.fx = event.subject.x
        event.subject.fy = event.subject.y
      })
      .on('drag', (event) => {
        event.subject.fx = event.x
        event.subject.fy = event.y
      })
      .on('end', (event) => {
        if (!event.active) simulation.alphaTarget(0)
        event.subject.fx = null
        event.subject.fy = null
      })

    node.call(drag)

    return () => {
      simulation.stop()
    }
  }, [displayGraph, typeColors, labelPrefix, rootId, navigate])

  // Sort types by their minimum depth in the graph (following provenance order)
  const types = useMemo(() => {
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

  return (
    <div className="graph-wrapper">
      <div ref={containerRef} className="graph-container" style={{ height: '600px', position: 'relative' }}>
        <svg ref={svgRef} width="100%" height="100%" />

        <div className="graph-timeline-axis" aria-hidden="true">
          <span>Older</span>
          <span className="graph-timeline-rule" />
          <span>Newer</span>
        </div>

        <div className="graph-legend">
          {labelPrefix && (
            <span className="graph-legend-prefix">
              Prefix: <code>{labelPrefix}</code>
            </span>
          )}
          {types.map(type => (
            <span key={type ?? 'unknown'} className="graph-legend-item">
              <svg width="14" height="14" style={{ flexShrink: 0 }}>
                <rect x="1" y="1" width="12" height="12" rx="2" ry="2" fill={typeColors(type)} />
              </svg>
              <span>{stripPrefix(type?.trim(), labelPrefix) || type?.trim() || 'Unknown'}</span>
            </span>
          ))}
        </div>

        {tooltip && (
          <div
            className="graph-tooltip"
            style={{
              position: 'fixed',
              left: tooltip.x,
              top: tooltip.y,
              background: 'white',
              border: '1px solid #ddd',
              borderRadius: '4px',
              padding: '8px 12px',
              fontSize: '12px',
              boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
              zIndex: 1000,
              pointerEvents: 'none',
              maxWidth: '22rem',
            }}
          >
            {tooltip.node.isGroup ? (
              <>
                <div style={{ fontWeight: 600, marginBottom: '4px' }}>Grouped Predecessors</div>
                <div><span style={{ color: '#666' }}>Hidden:</span> {(tooltip.node as GroupNode).count} predecessors</div>
              </>
            ) : (
              <NodeDetails node={tooltip.node as GraphNode} />
            )}
          </div>
        )}
      </div>

      {selectedGroup && (
        <PredecessorListModal
          nodes={graph.nodes.filter(n => selectedGroup.hiddenNodeIds.includes(n.id))}
          onClose={() => setSelectedGroup(null)}
        />
      )}
    </div>
  )
}
