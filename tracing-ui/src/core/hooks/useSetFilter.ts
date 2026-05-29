import { useState, useMemo, useCallback } from 'react'

export function useSetFilter(allValues: string[]) {
  const [selectedOverride, setSelectedOverride] = useState<Set<string> | null>(null)
  const selected = useMemo(
    () => selectedOverride ?? new Set(allValues),
    [selectedOverride, allValues],
  )

  const toggle = useCallback((value: string) => {
    setSelectedOverride(prev => {
      const current = new Set(prev ?? allValues)
      if (current.has(value)) {
        current.delete(value)
      } else {
        current.add(value)
      }
      return current.size === allValues.length ? null : current
    })
  }, [allValues])

  const selectAll = useCallback(() => setSelectedOverride(null), [])
  const clearAll = useCallback(() => setSelectedOverride(new Set()), [])

  return { selected, toggle, selectAll, clearAll, isActive: selectedOverride !== null }
}
