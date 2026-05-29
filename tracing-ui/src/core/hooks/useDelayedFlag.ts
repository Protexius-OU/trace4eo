import { useEffect, useState } from 'react'

export function useDelayedFlag(active: boolean, delayMs: number): boolean {
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
