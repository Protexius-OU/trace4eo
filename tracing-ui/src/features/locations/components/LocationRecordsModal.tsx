import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords, fetchFilterOptions } from '../../provenance/api/provenanceApi'
import { FilterDropdown, DataIdFilter } from '../../provenance/components/Filters'
import { useCheckboxFilter } from '../../provenance/utils/useCheckboxFilter'
import { getSignerDomain } from '../../provenance/utils/signerIdentity'
import type { AttributeChip, RecordFilters } from '../../provenance/types/provenance'
import Pagination from '@/core/components/Pagination'
import '../../provenance/components/Modal.css'

interface Props {
  countryName: string
  countryKey: string
  onClose: () => void
}

const PAGE_SIZE = 20

export default function LocationRecordsModal({ countryName, countryKey, onClose }: Props) {
  const authFetch = useAuthFetch()
  const [dataIdQuery, setDataIdQuery] = useState('')
  const locationAttribute = useMemo<AttributeChip[]>(
    () => [{ key: 'location', value: countryKey }],
    [countryKey],
  )
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<RecordFilters>({ attributes: locationAttribute })

  const handleFilterChange = useCallback((next: RecordFilters) => {
    setFilters({ ...next, attributes: locationAttribute })
    setPage(0)
  }, [locationAttribute])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const {
    data: filterOptions,
    isLoading: filterOptionsLoading,
    error: filterOptionsError,
  } = useQuery({
    queryKey: ['filterOptions'],
    queryFn: fetchFilterOptions,
    staleTime: 60_000,
  })

  const { data, isLoading: recordsLoading, error: recordsError } = useQuery({
    queryKey: ['recordsByLocation', countryKey, page, filters],
    queryFn: () => fetchRecords(page, PAGE_SIZE, filters),
  const { data, isLoading, error } = useQuery({
    queryKey: ['recordsByLocation', countryKey],
    queryFn: () => fetchRecords(authFetch, 0, MAX_ROWS_PER_COUNTRY, { attributes: `location=${countryKey}` }),
  })

  const isLoading = recordsLoading || filterOptionsLoading
  const error = recordsError ?? filterOptionsError

  const signerDisplayValues = useMemo(
    () => new Map((filterOptions?.signerIdentities ?? []).map(email => [email, getSignerDomain(email)])),
    [filterOptions],
  )

  const typeFilter = useCheckboxFilter('dataTypes', filterOptions?.dataTypes ?? [], filters, handleFilterChange)
  const signerFilter = useCheckboxFilter('signerIdentities', filterOptions?.signerIdentities ?? [], filters, handleFilterChange)

  const handleDataIdChange = useCallback((value: string) => {
    handleFilterChange({ ...filters, dataId: value || undefined })
  }, [filters, handleFilterChange])

  const records = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content-wide" onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>
            Records from {countryName}
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
          {!isLoading && !error && filterOptions && (
            <table>
              <thead>
                <tr>
                  <th>
                    <DataIdFilter value={filters.dataId ?? ''} onChange={handleDataIdChange} />
                  </th>
                  <th>
                    <FilterDropdown
                      label="Type"
                      values={filterOptions.dataTypes}
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
                      values={filterOptions.signerIdentities}
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
                {records.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="td-empty">No records match the current filters</td>
                  </tr>
                ) : (
                  records.map(record => (
                    <tr key={record.id}>
                      <td>
                        <Link
                          to={`/records/${record.id}/graph`}
                          style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}
                          onClick={onClose}
                        >
                          {record.metadata.dataId || record.id}
                        </Link>
                      </td>
                      <td>
                        <span className="badge badge-type">{record.metadata.dataType || 'Unknown'}</span>
                      </td>
                      <td>
                        {record.signature?.signingTime
                          ? new Date(record.signature.signingTime).toLocaleDateString()
                          : '-'}
                      </td>
                      <td style={{ fontSize: '0.8rem' }}>
                        {record.signature?.details?.signerIdentity
                          ? getSignerDomain(record.signature.details.signerIdentity)
                          : '-'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
        />
        {data && (
          <p style={{ textAlign: 'center', marginTop: '1rem', color: '#666' }}>
            {totalElements} total records
          </p>
        )}
      </div>
    </div>
  )
}
