import { useState, useCallback } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { fetchRecords, fetchFilterOptions } from '../api/provenanceApi'
import type { RecordFilters } from '../types/provenance'
import RecordTable from '../components/RecordTable'
import Pagination from '@/core/components/Pagination'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import LoadingBar from '@/core/components/LoadingBar'
import { useDelayedFlag } from '@/core/hooks/useDelayedFlag'

export default function RecordListPage() {
  const authFetch = useAuthFetch()
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<RecordFilters>({})

  const { data: filterOptions } = useQuery({
    queryKey: ['filterOptions'],
    queryFn: () => fetchFilterOptions(authFetch),
    staleTime: 60_000,
  })

  const { data, isLoading, isFetching, error } = useQuery({
    queryKey: ['records', page, filters],
    queryFn: () => fetchRecords(authFetch, page, 20, filters),
    placeholderData: keepPreviousData,
  })

  const showRefetchBar = useDelayedFlag(isFetching && !isLoading, 300)

  const handleFilterChange = useCallback((newFilters: RecordFilters) => {
    setFilters(newFilters)
    setPage(0)
  }, [])

  return (
    <div>
      <h1>Provenance Records</h1>

      {error && <p className="error">Error loading records: {String(error)}</p>}

      {filterOptions && (
        <>
          {showRefetchBar && <LoadingBar />}
          <RecordTable
            records={data?.content ?? []}
            filterOptions={filterOptions}
            filters={filters}
            onFilterChange={handleFilterChange}
            isFetching={isFetching}
            isLoading={isLoading}
          />
          {data && (
            <>
              <Pagination
                page={page}
                totalPages={data.totalPages}
                onPageChange={setPage}
              />
              <p className="pagination-summary">
                {data.totalElements} total records
              </p>
            </>
          )}
        </>
      )}
    </div>
  )
}
