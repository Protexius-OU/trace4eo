import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords } from '../api/provenanceApi'
import RecordTable from '../components/RecordTable'
import Pagination from '@/core/components/Pagination'

export default function RecordListPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading, error } = useQuery({
    queryKey: ['records', page],
    queryFn: () => fetchRecords(page, 20),
  })

  return (
    <div>
      <h1>Provenance Records</h1>

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
