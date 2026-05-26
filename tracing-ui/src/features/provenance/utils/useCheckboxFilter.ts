import { useCallback } from 'react'
import type { RecordFilters } from '../types/provenance'

type CheckboxFilterKey = 'dataTypes' | 'signerIdentities'

export function useCheckboxFilter(
  filterKey: CheckboxFilterKey,
  allValues: string[],
  filters: RecordFilters,
  onFilterChange: (f: RecordFilters) => void,
) {
  const selected = new Set(filters[filterKey] ?? allValues)

  const toggle = useCallback((value: string) => {
    const current = new Set(filters[filterKey] ?? allValues)
    if (current.has(value)) {
      current.delete(value)
    } else {
      current.add(value)
    }
    const updated = current.size === allValues.length ? undefined : [...current]
    onFilterChange({ ...filters, [filterKey]: updated })
  }, [filterKey, allValues, filters, onFilterChange])

  const selectAll = useCallback(() => {
    onFilterChange({ ...filters, [filterKey]: undefined })
  }, [filterKey, filters, onFilterChange])

  const clearAll = useCallback(() => {
    onFilterChange({ ...filters, [filterKey]: [] })
  }, [filterKey, filters, onFilterChange])

  return { selected, toggle, selectAll, clearAll }
}
