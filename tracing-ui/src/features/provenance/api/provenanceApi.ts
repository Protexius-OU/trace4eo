import { authFetch } from '../../../core/auth/authFetch'
import type { ProvenanceRecord, ProvenanceGraph, PagedResponse, RecordFilters, FilterOptions, VerificationResult, FileVerificationResponse, Sentinel2VerificationResponse, Sentinel2HashCheckResponse } from '../types/provenance'

const API_BASE = '/api/provenance'

const EMPTY_PAGE: PagedResponse<ProvenanceRecord> = {
  content: [], totalElements: 0, totalPages: 0, page: 0, size: 0
}

export async function fetchRecords(
  page: number = 0,
  size: number = 20,
  filters: RecordFilters = {}
): Promise<PagedResponse<ProvenanceRecord>> {
  // An explicitly empty filter array means "match nothing" — no need to ask the server
  if (filters.dataTypes?.length === 0 || filters.signerIdentities?.length === 0) {
    return EMPTY_PAGE
  }

  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  })

  if (filters.dataTypes) {
    filters.dataTypes.forEach(t => params.append('dataType', t))
  }
  if (filters.dataId) params.set('dataId', filters.dataId)
  if (filters.signerIdentities) {
    filters.signerIdentities.forEach(s => params.append('signerIdentity', s))
  }

  const response = await authFetch(`${API_BASE}?${params}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch records: ${response.statusText}`)
  }
  return response.json()
}

export async function fetchFilterOptions(): Promise<FilterOptions> {
  const response = await authFetch(`${API_BASE}/filters`)
  if (!response.ok) {
    throw new Error(`Failed to fetch filter options: ${response.statusText}`)
  }
  return response.json()
}

export async function fetchRecord(id: string): Promise<ProvenanceRecord> {
  const response = await authFetch(`${API_BASE}/${id}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch record: ${response.statusText}`)
  }
  return response.json()
}

export async function fetchGraph(id: string): Promise<ProvenanceGraph> {
  const response = await authFetch(`${API_BASE}/${id}/graph`)
  if (!response.ok) {
    throw new Error(`Failed to fetch graph: ${response.statusText}`)
  }
  return response.json()
}

export async function downloadZip(id: string): Promise<void> {
  const response = await authFetch(`${API_BASE}/${id}/zip`)
  if (!response.ok) {
    throw new Error(`Download failed: ${response.statusText}`)
  }
  const blob = await response.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `provenance-${id}.zip`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

export async function verifyRecord(id: string): Promise<VerificationResult> {
  const response = await authFetch(`${API_BASE}/${id}/verify`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Verification failed: ${response.statusText}`)
  }
  return response.json()
}

export async function verifyFileHashes(
  id: string,
  inputs: Array<{ filename: string; hashValue: string }>
): Promise<FileVerificationResponse> {
  const response = await authFetch(`${API_BASE}/${id}/verify-files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(inputs),
  })
  if (!response.ok) {
    throw new Error(`File verification failed: ${response.statusText}`)
  }
  return response.json()
}

export async function verifySentinel2Trace(id: string): Promise<Sentinel2VerificationResponse> {
  const response = await authFetch(`${API_BASE}/sentinel-2/${id}/verify-trace`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Trace verification failed: ${response.statusText}`)
  }
  return response.json()
}

export async function verifySentinel2Files(
  id: string,
  files: Array<{ filename: string; hashHex: string }>,
): Promise<Sentinel2HashCheckResponse> {
  const response = await authFetch(`${API_BASE}/sentinel-2/${id}/verify-files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ files }),
  })
  if (!response.ok) {
    throw new Error(`Sentinel-2 verification failed: ${response.statusText}`)
  }
  return response.json()
}
