import type { FetchFn } from '../../../core/auth/authFetch'
import type { LocationCount } from '../types/locations'

const API_BASE = '/api/provenance'

export async function fetchLocationCounts(fetch: FetchFn): Promise<LocationCount[]> {
  const response = await fetch(`${API_BASE}/location-counts`)
  if (!response.ok) {
    throw new Error(`Failed to fetch location counts: ${response.statusText}`)
  }
  return response.json()
}
