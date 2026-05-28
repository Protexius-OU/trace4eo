import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Modal } from '@/core/components/Modal'
import { TypeBadge } from '@/core/components/TypeBadge'
import { useSetFilter } from '@/core/hooks/useSetFilter'
import type { GraphNode } from '../types/provenance'
import { FilterDropdown, DataIdFilter } from './Filters'
import { getSignerDomain } from '../utils/signerIdentity'
import { formatDate } from '../utils/labelUtils'

interface Props {
  nodes: GraphNode[]
  onClose: () => void
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

  const typeFilter = useSetFilter(allTypes)
  const signerFilter = useSetFilter(allSigners)

  const filtered = useMemo(() => {
    const query = dataIdQuery.trim().toLowerCase()
    return nodes.filter(node => {
      if (query && !(node.dataId ?? '').toLowerCase().includes(query)) return false
      if (typeFilter.isActive && !typeFilter.selected.has(node.dataType)) return false
      if (signerFilter.isActive && !signerFilter.selected.has(node.signerIdentity ?? '')) return false
      return true
    })
  }, [nodes, dataIdQuery, typeFilter.isActive, typeFilter.selected, signerFilter.isActive, signerFilter.selected])

  const title = (
    <>
      Collapsed Predecessors ({filtered.length}
      {filtered.length !== nodes.length && <> of {nodes.length}</>})
    </>
  )

  return (
    <Modal title={title} onClose={onClose}>
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
                      className="modal-data-id-link"
                      onClick={onClose}
                    >
                      {node.dataId || node.id}
                    </Link>
                  </td>
                  <td>
                    <TypeBadge type={node.dataType} />
                  </td>
                  <td>
                    {formatDate(node.signingTime) || '-'}
                  </td>
                  <td className="modal-cell-sm">
                    {node.signerIdentity ? getSignerDomain(node.signerIdentity) : '-'}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Modal>
  )
}
