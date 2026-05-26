import type { FetchFn } from '../../../core/auth/authFetch'
import type { LocationCount } from '../types/locations'

const API_BASE = '/api/provenance'

export async function fetchChainLocationCounts(
  fetch: FetchFn,
  rootId: string,
): Promise<LocationCount[]> {
  const response = await fetch(`${API_BASE}/${rootId}/location-counts`)
  if (!response.ok) {
    throw new Error(`Failed to fetch chain location counts: ${response.statusText}`)
  }
  return response.json()
}
