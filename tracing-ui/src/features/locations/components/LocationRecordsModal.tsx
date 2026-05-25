import { useEffect, useMemo, useState, useCallback } from 'react'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords } from '../../provenance/api/provenanceApi'
import { FilterDropdown, DataIdFilter } from '../../provenance/components/Filters'
import { getSignerDomain } from '../../provenance/utils/signerIdentity'
import type { ProvenanceRecord } from '../../provenance/types/provenance'
import '../../provenance/components/Modal.css'

interface Props {
  countryName: string
  countryKey: string
  onClose: () => void
}

interface Row {
  id: string
  dataId: string
  dataType: string
  signingTime: string | null
  signerIdentity: string | null
}

const MAX_ROWS_PER_COUNTRY = 200

function useCheckboxFilter(allValues: string[]) {
  const [override, setOverride] = useState<Set<string> | null>(null)
  const selected = useMemo(
    () => override ?? new Set(allValues),
    [override, allValues],
  )

  const toggle = useCallback((value: string) => {
    setOverride(prev => {
      const current = new Set(prev ?? allValues)
      if (current.has(value)) {
        current.delete(value)
      } else {
        current.add(value)
      }
      return current.size === allValues.length ? null : current
    })
  }, [allValues])

  const selectAll = useCallback(() => setOverride(null), [])
  const clearAll = useCallback(() => setOverride(new Set()), [])

  return { selected, toggle, selectAll, clearAll, isActive: override !== null }
}

function toRow(record: ProvenanceRecord): Row {
  return {
    id: record.id,
    dataId: record.metadata.dataId,
    dataType: record.metadata.dataType,
    signingTime: record.signature?.signingTime ?? null,
    signerIdentity: record.signature?.details?.signerIdentity ?? null,
  }
}

export default function LocationRecordsModal({ countryName, countryKey, onClose }: Props) {
  const authFetch = useAuthFetch()
  const [dataIdQuery, setDataIdQuery] = useState('')

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const { data, isLoading, error } = useQuery({
    queryKey: ['recordsByLocation', countryKey],
    queryFn: () => fetchRecords(authFetch, 0, MAX_ROWS_PER_COUNTRY, { attributes: `location=${countryKey}` }),
  })

  const rows = useMemo<Row[]>(() => (data?.content ?? []).map(toRow), [data])
  const totalElements = data?.totalElements ?? rows.length
  const isTruncated = totalElements > rows.length

  const allTypes = useMemo(
    () => Array.from(new Set(rows.map(r => r.dataType).filter(Boolean))).sort(),
    [rows],
  )
  const allSigners = useMemo(
    () => Array.from(new Set(rows.map(r => r.signerIdentity).filter((s): s is string => !!s))).sort(),
    [rows],
  )
  const signerDisplayValues = useMemo(
    () => new Map(allSigners.map(email => [email, getSignerDomain(email)])),
    [allSigners],
  )

  const typeFilter = useCheckboxFilter(allTypes)
  const signerFilter = useCheckboxFilter(allSigners)

  const filtered = useMemo(() => {
    const query = dataIdQuery.trim().toLowerCase()
    return rows.filter(row => {
      if (query && !(row.dataId ?? '').toLowerCase().includes(query)) return false
      if (typeFilter.isActive && !typeFilter.selected.has(row.dataType)) return false
      if (signerFilter.isActive && !signerFilter.selected.has(row.signerIdentity ?? '')) return false
      return true
    })
  }, [rows, dataIdQuery, typeFilter.isActive, typeFilter.selected, signerFilter.isActive, signerFilter.selected])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content-wide" onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>
            Records from {countryName} ({filtered.length}
            {filtered.length !== rows.length && <> of {rows.length}</>}
            {isTruncated && <> — first {rows.length} of {totalElements}</>})
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
          {isLoading && <p>Loading records…</p>}
          {error && (
            <p style={{ color: '#b91c1c' }}>
              Error loading records: {error instanceof Error ? error.message : 'Unknown error'}
            </p>
          )}
          {!isLoading && !error && (
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
                    <td colSpan={4} className="td-empty">No records match the current filters</td>
                  </tr>
                ) : (
                  filtered.map(row => (
                    <tr key={row.id}>
                      <td>
                        <Link
                          to={`/records/${row.id}/graph`}
                          style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}
                          onClick={onClose}
                        >
                          {row.dataId || row.id}
                        </Link>
                      </td>
                      <td>
                        <span className="badge badge-type">{row.dataType || 'Unknown'}</span>
                      </td>
                      <td>
                        {row.signingTime
                          ? new Date(row.signingTime).toLocaleDateString()
                          : '-'}
                      </td>
                      <td style={{ fontSize: '0.8rem' }}>
                        {row.signerIdentity ? getSignerDomain(row.signerIdentity) : '-'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
