import { useEffect, useMemo, useRef, useState } from 'react'
import * as d3 from 'd3'
import type { ProvenanceGraph, GraphNode } from '../types/provenance'

interface Props {
  graph: ProvenanceGraph
}

interface SimNode extends GraphNode {
  x?: number
  y?: number
  fx?: number | null
  fy?: number | null
}

interface SimLink {
  source: SimNode | string
  target: SimNode | string
}

export default function ProvenanceGraphViewer({ graph }: Props) {
  const svgRef = useRef<SVGSVGElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [tooltip, setTooltip] = useState<{ node: GraphNode; x: number; y: number } | null>(null)

  const typeColors = useMemo(() => d3.scaleOrdinal(d3.schemeCategory10), [])

  useEffect(() => {
    if (!svgRef.current || !containerRef.current) return

    const svg = d3.select(svgRef.current)
    svg.selectAll('*').remove()

    const width = containerRef.current.clientWidth
    const height = containerRef.current.clientHeight || 600

    svg.attr('viewBox', `0 0 ${width} ${height}`)

    const links: SimLink[] = graph.edges.map(e => ({
      source: e.sourceId,
      target: e.targetId
    }))

    const nodes: SimNode[] = graph.nodes.map(n => ({ ...n }))

    const g = svg.append('g')

    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 4])
      .on('zoom', (event) => {
        g.attr('transform', event.transform)
      })

    svg.call(zoom)

    svg.append('defs').append('marker')
      .attr('id', 'arrowhead')
      .attr('viewBox', '-0 -5 10 10')
      .attr('refX', 20)
      .attr('refY', 0)
      .attr('orient', 'auto')
      .attr('markerWidth', 6)
      .attr('markerHeight', 6)
      .append('path')
      .attr('d', 'M 0,-5 L 10,0 L 0,5')
      .attr('fill', '#999')

    const link = g.append('g')
      .attr('class', 'links')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#999')
      .attr('stroke-opacity', 0.6)
      .attr('stroke-width', 2)
      .attr('marker-end', 'url(#arrowhead)')

    const node = g.append('g')
      .attr('class', 'nodes')
      .selectAll<SVGGElement, SimNode>('g')
      .data(nodes)
      .join('g')

    node.each(function(d) {
      const el = d3.select(this)
      const color = typeColors(d.dataType)
      const strokeColor = d3.color(color)?.darker(0.5)?.toString() ?? color

      el.append('circle')
        .attr('r', 12 + Math.min(d.predecessorCount, 10))
        .attr('fill', color)
        .attr('stroke', strokeColor)
        .attr('stroke-width', 2)
        .style('cursor', 'pointer')
    })

    node.append('text')
      .attr('dy', d => 22 + Math.min(d.predecessorCount, 10))
      .attr('text-anchor', 'middle')
      .attr('font-size', '10px')
      .attr('fill', '#333')
      .text(d => truncate(d.dataType, 12))

    node.on('mouseenter', function(event, d) {
      // Pin node in place while hovering to prevent jumping
      d.fx = d.x
      d.fy = d.y
      setTooltip({ node: d, x: event.pageX + 15, y: event.pageY + 15 })
    })
    .on('mouseleave', (_event, d) => {
      // Unpin node when mouse leaves
      d.fx = null
      d.fy = null
      setTooltip(null)
    })

    const simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink<SimNode, SimLink>(links).id(d => d.id).distance(100))
      .force('charge', d3.forceManyBody().strength(-400))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('y', d3.forceY<SimNode>().y(d => 100 + d.depth * 120).strength(0.3))
      .on('tick', () => {
        link
          .attr('x1', d => (d.source as SimNode).x ?? 0)
          .attr('y1', d => (d.source as SimNode).y ?? 0)
          .attr('x2', d => (d.target as SimNode).x ?? 0)
          .attr('y2', d => (d.target as SimNode).y ?? 0)

        node.attr('transform', d => `translate(${d.x ?? 0},${d.y ?? 0})`)
      })

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
  }, [graph, typeColors])

  const types = [...new Set(graph.nodes.map(n => n.dataType))].sort()

  const hasMissingPredecessors = graph.metadata.missingPredecessors.length > 0

  return (
    <div className="graph-wrapper">
      <div className="graph-info">
        {graph.metadata.totalNodes} {graph.metadata.totalNodes === 1 ? 'record' : 'records'} in chain
        {hasMissingPredecessors && (
          <span style={{ marginLeft: '1rem', color: '#666' }}>
            ({graph.metadata.missingPredecessors.length} predecessors not yet loaded)
          </span>
        )}
      </div>

      <div className="graph-legend">
        {types.map(type => (
          <span key={type} className="legend-item">
            <svg width="16" height="16" style={{ verticalAlign: 'middle', marginRight: '4px' }}>
              <circle cx="8" cy="8" r="6" fill={typeColors(type)} />
            </svg>
            {type || 'Unknown'}
          </span>
        ))}
      </div>

      <div ref={containerRef} className="graph-container" style={{ height: '600px', position: 'relative' }}>
        <svg ref={svgRef} width="100%" height="100%" />

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
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: '4px' }}>{tooltip.node.dataType || 'Unknown'}</div>
            <div><span style={{ color: '#666' }}>ID:</span> <span style={{ fontFamily: 'monospace', fontSize: '10px' }}>{tooltip.node.id}</span></div>
            <div><span style={{ color: '#666' }}>Data ID:</span> {tooltip.node.dataId || 'N/A'}</div>
            <div><span style={{ color: '#666' }}>Signed:</span> {tooltip.node.signingTime ? new Date(tooltip.node.signingTime).toLocaleString() : 'N/A'}</div>
            <div><span style={{ color: '#666' }}>Predecessors:</span> {tooltip.node.predecessorCount}</div>
          </div>
        )}
      </div>
    </div>
  )
}

function truncate(str: string | null | undefined, maxLen: number): string {
  if (!str) return ''
  return str.length > maxLen ? str.substring(0, maxLen - 1) + '...' : str
}
