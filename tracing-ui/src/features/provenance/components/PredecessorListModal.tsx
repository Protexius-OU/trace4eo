import { useMemo, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import './Modal.css'
import type { GraphNode } from '../types/provenance'
import { FilterDropdown, DataIdFilter } from './Filters'
import { getSignerDomain } from '../utils/signerIdentity'

interface Props {
  nodes: GraphNode[]
  onClose: () => void
}

function useNodeCheckboxFilter(allValues: string[]) {
  const [selectedOverride, setSelectedOverride] = useState<Set<string> | null>(null)
  const selected = selectedOverride ?? new Set(allValues)

  const toggle = useCallback((value: string) => {
    setSelectedOverride(prev => {
      const current = new Set(prev ?? allValues)
      if (current.has(value)) {
        current.delete(value)
      } else {
        current.add(value)
      }
      return current.size === allValues.length ? null : current
    })
  }, [allValues])

  const selectAll = useCallback(() => setSelectedOverride(null), [])
  const clearAll = useCallback(() => setSelectedOverride(new Set()), [])

  return { selected, toggle, selectAll, clearAll, isActive: selectedOverride !== null }
}

export default function PredecessorListModal({ nodes, onClose }: Props) {
  const [dataIdQuery, setDataIdQuery] = useState('')

  const allTypes = useMemo(
    () => Array.from(new Set(nodes.map(n => n.dataType).filter(Boolean))).sort(),
    [nodes]
  )
  const allSigners = useMemo(
    () => Array.from(new Set(nodes.map(n => n.signerIdentity).filter((s): s is string => !!s))).sort(),
    [nodes]
  )
  const signerDisplayValues = useMemo(
    () => new Map(allSigners.map(email => [email, getSignerDomain(email)])),
    [allSigners]
  )

  const typeFilter = useNodeCheckboxFilter(allTypes)
  const signerFilter = useNodeCheckboxFilter(allSigners)

  const filtered = useMemo(() => {
    const query = dataIdQuery.trim().toLowerCase()
    return nodes.filter(node => {
      if (query && !(node.dataId ?? '').toLowerCase().includes(query)) return false
      if (typeFilter.isActive && !typeFilter.selected.has(node.dataType)) return false
      if (signerFilter.isActive && !signerFilter.selected.has(node.signerIdentity ?? '')) return false
      return true
    })
  }, [nodes, dataIdQuery, typeFilter.isActive, typeFilter.selected, signerFilter.isActive, signerFilter.selected])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content-wide" onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>
            Collapsed Predecessors ({filtered.length}
            {filtered.length !== nodes.length && <> of {nodes.length}</>})
          </h2>
          <button
            className="btn btn-secondary"
            onClick={onClose}
            style={{ padding: '0.25rem 0.75rem' }}
          >
            X
          </button>
        </div>
        <div className="modal-table-scroll">
          <table>
            <thead>
              <tr>
                <th>
                  <DataIdFilter value={dataIdQuery} onChange={setDataIdQuery} />
                </th>
                <th>
                  <FilterDropdown
                    label="Type"
                    values={allTypes}
                    selected={typeFilter.selected}
                    onToggle={typeFilter.toggle}
                    onSelectAll={typeFilter.selectAll}
                    onClearAll={typeFilter.clearAll}
                  />
                </th>
                <th>Signed</th>
                <th>
                  <FilterDropdown
                    label="Signed by"
                    values={allSigners}
                    displayValues={signerDisplayValues}
                    selected={signerFilter.selected}
                    onToggle={signerFilter.toggle}
                    onSelectAll={signerFilter.selectAll}
                    onClearAll={signerFilter.clearAll}
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={4} className="td-empty">No predecessors match the current filters</td>
                </tr>
              ) : (
                filtered.map(node => (
                  <tr key={node.id}>
                    <td>
                      <Link
                        to={`/records/${node.id}/graph`}
                        style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}
                        onClick={onClose}
                      >
                        {node.dataId || node.id}
                      </Link>
                    </td>
                    <td>
                      <span className="badge badge-type">{node.dataType || 'Unknown'}</span>
                    </td>
                    <td>
                      {node.signingTime
                        ? new Date(node.signingTime).toLocaleDateString()
                        : '-'}
                    </td>
                    <td style={{ fontSize: '0.8rem' }}>
                      {node.signerIdentity ? getSignerDomain(node.signerIdentity) : '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
