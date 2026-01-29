import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords } from '../api/provenanceApi'
import RecordTable from '../components/RecordTable'
import RecordFilters from '../components/RecordFilters'
import Pagination from '@/core/components/Pagination'
import type { RecordFilters as Filters } from '../types/provenance'

export default function RecordListPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<Filters>({})

  const { data, isLoading, error } = useQuery({
    queryKey: ['records', page, filters],
    queryFn: () => fetchRecords(page, 20, filters),
  })

  const handleFilterChange = (newFilters: Filters) => {
    setFilters(newFilters)
    setPage(0)
  }

  return (
    <div>
      <h1>Provenance Records</h1>

      <RecordFilters filters={filters} onFilterChange={handleFilterChange} />

      {isLoading && <p className="loading">Loading records...</p>}

      {error && <p className="error">Error loading records: {String(error)}</p>}

      {data && (
        <>
          <RecordTable records={data.content} />
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
