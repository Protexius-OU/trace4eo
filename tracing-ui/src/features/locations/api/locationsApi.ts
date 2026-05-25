import { authFetch } from '../../../core/auth/authFetch'
import type { LocationCount } from '../types/locations'

const API_BASE = '/api/provenance'

export async function fetchLocationCounts(): Promise<LocationCount[]> {
  const response = await authFetch(`${API_BASE}/location-counts`)
  if (!response.ok) {
    throw new Error(`Failed to fetch location counts: ${response.statusText}`)
  }
  return response.json()
}
