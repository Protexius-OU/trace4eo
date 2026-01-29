import { useState } from 'react'
import type { RecordFilters } from '../types/provenance'

interface Props {
  filters: RecordFilters
  onFilterChange: (filters: RecordFilters) => void
}

export default function RecordFiltersComponent({ filters, onFilterChange }: Props) {
  const [localFilters, setLocalFilters] = useState(filters)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onFilterChange(localFilters)
  }

  const handleClear = () => {
    const cleared: RecordFilters = {}
    setLocalFilters(cleared)
    onFilterChange(cleared)
  }

  return (
    <form className="filters" onSubmit={handleSubmit}>
      <div className="filter-group">
        <label htmlFor="dataType">Record Type</label>
        <input
          id="dataType"
          type="text"
          placeholder="e.g. satellite-image"
          value={localFilters.dataType ?? ''}
          onChange={(e) => setLocalFilters({ ...localFilters, dataType: e.target.value })}
        />
      </div>
      <div className="filter-group">
        <label htmlFor="dataId">Data ID</label>
        <input
          id="dataId"
          type="text"
          placeholder="Search by data ID"
          value={localFilters.dataId ?? ''}
          onChange={(e) => setLocalFilters({ ...localFilters, dataId: e.target.value })}
        />
      </div>
      <div className="filter-group" style={{ justifyContent: 'flex-end' }}>
        <label>&nbsp;</label>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button type="submit" className="btn btn-primary">Apply</button>
          <button type="button" className="btn btn-secondary" onClick={handleClear}>Clear</button>
        </div>
      </div>
    </form>
  )
}
