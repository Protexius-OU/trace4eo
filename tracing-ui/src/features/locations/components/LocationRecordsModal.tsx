import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { Link } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
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
  chainRootId?: string
  onClose: () => void
}

const PAGE_SIZE = 20

function useDelayedFlag(active: boolean, delayMs: number): boolean {
  const [delayed, setDelayed] = useState(false)
  useEffect(() => {
    if (!active) {
      setDelayed(false)
      return
    }
    const id = window.setTimeout(() => setDelayed(true), delayMs)
    return () => window.clearTimeout(id)
  }, [active, delayMs])
  return delayed
}

export default function LocationRecordsModal({ countryName, countryKey, chainRootId, onClose }: Props) {
  const authFetch = useAuthFetch()
  const locationAttribute = useMemo<AttributeChip[]>(
    () => [{ key: 'location', value: countryKey }],
    [countryKey],
  )
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<RecordFilters>({
    attributes: locationAttribute,
    inChainOf: chainRootId,
  })

  const handleFilterChange = useCallback((next: RecordFilters) => {
    setFilters({ ...next, attributes: locationAttribute, inChainOf: chainRootId })
    setPage(0)
  }, [locationAttribute, chainRootId])

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
    queryFn: () => fetchFilterOptions(authFetch),
    staleTime: 60_000,
  })

  const {
    data,
    isLoading: recordsLoading,
    isFetching: recordsFetching,
    error: recordsError,
  } = useQuery({
    queryKey: ['recordsByLocation', countryKey, chainRootId ?? null, page, filters],
    queryFn: () => fetchRecords(authFetch, page, PAGE_SIZE, filters),
    placeholderData: keepPreviousData,
  })

  const initialLoading = (recordsLoading && !data) || filterOptionsLoading
  const isRefetching = recordsFetching && !recordsLoading
  const showRefetchIndicator = useDelayedFlag(isRefetching, 300)
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
        <div className={`modal-table-scroll${showRefetchIndicator ? ' is-refetching' : ''}`}>
          {showRefetchIndicator && <div className="modal-loading-bar" aria-hidden="true" />}
          {initialLoading && <p>Loading records…</p>}
          {error && (
            <p style={{ color: '#b91c1c' }}>
              Error loading records: {error instanceof Error ? error.message : 'Unknown error'}
            </p>
          )}
          {!initialLoading && !error && filterOptions && (
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
