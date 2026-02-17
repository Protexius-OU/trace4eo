import { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords, fetchFilterOptions } from '../api/provenanceApi'
import type { RecordFilters } from '../types/provenance'
import RecordTable from '../components/RecordTable'
import Pagination from '@/core/components/Pagination'

export default function RecordListPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<RecordFilters>({})

  const { data: filterOptions } = useQuery({
    queryKey: ['filterOptions'],
    queryFn: fetchFilterOptions,
    staleTime: 60_000,
  })

  const { data, isLoading, error } = useQuery({
    queryKey: ['records', page, filters],
    queryFn: () => fetchRecords(page, 20, filters),
  })

  const handleFilterChange = useCallback((newFilters: RecordFilters) => {
    setFilters(newFilters)
    setPage(0)
  }, [])

  return (
    <div>
      <h1>Provenance Records</h1>

      {isLoading && <p className="loading">Loading records...</p>}

      {error && <p className="error">Error loading records: {String(error)}</p>}

      {data && filterOptions && (
        <>
          <RecordTable
            records={data.content}
            filterOptions={filterOptions}
            filters={filters}
            onFilterChange={handleFilterChange}
          />
          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={setPage}
          />
          <p style={{ textAlign: 'center', marginTop: '1rem', color: '#666' }}>
            {data.totalElements} total records
          </p>
        </>
      )}
    </div>
  )
}
