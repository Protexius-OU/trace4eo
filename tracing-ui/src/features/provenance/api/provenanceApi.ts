import type { FetchFn } from '../../../core/auth/authFetch'
import type { ProvenanceRecord, ProvenanceGraph, PagedResponse, RecordFilters, FilterOptions, VerificationResult, FileVerificationResponse, Sentinel2VerificationResponse, Sentinel2HashCheckResponse } from '../types/provenance'

const API_BASE = '/api/provenance'

const EMPTY_PAGE: PagedResponse<ProvenanceRecord> = {
  content: [], totalElements: 0, totalPages: 0, page: 0, size: 0
}

export async function fetchRecords(
  fetch: FetchFn,
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
  if (filters.attributes) {
    filters.attributes
      .trim()
      .split(/\s+/)
      .filter(t => t.includes('='))
      .forEach(t => params.append('attribute', t))
  }

  const response = await fetch(`${API_BASE}?${params}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch records: HTTP ${response.status}`)
  }
  return response.json()
}

export async function fetchFilterOptions(fetch: FetchFn): Promise<FilterOptions> {
  const response = await fetch(`${API_BASE}/filters`)
  if (!response.ok) {
    throw new Error(`Failed to fetch filter options: HTTP ${response.status}`)
  }
  return response.json()
}

export async function fetchRecord(fetch: FetchFn, id: string): Promise<ProvenanceRecord> {
  const response = await fetch(`${API_BASE}/${id}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch record: HTTP ${response.status}`)
  }
  return response.json()
}

export async function fetchGraph(fetch: FetchFn, id: string): Promise<ProvenanceGraph> {
  const response = await fetch(`${API_BASE}/${id}/graph`)
  if (!response.ok) {
    throw new Error(`Failed to fetch graph: HTTP ${response.status}`)
  }
  return response.json()
}

export async function downloadZip(fetch: FetchFn, id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}/zip`)
  if (!response.ok) {
    throw new Error(`Download failed: HTTP ${response.status}`)
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

export async function verifyRecord(fetch: FetchFn, id: string): Promise<VerificationResult> {
  const response = await fetch(`${API_BASE}/${id}/verify`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Verification failed: HTTP ${response.status}`)
  }
  return response.json()
}

export async function verifyFileHashes(
  fetch: FetchFn,
  id: string,
  inputs: Array<{ filename: string; hashValue: string }>
): Promise<FileVerificationResponse> {
  const response = await fetch(`${API_BASE}/${id}/verify-files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(inputs),
  })
  if (!response.ok) {
    throw new Error(`File verification failed: HTTP ${response.status}`)
  }
  return response.json()
}

export async function verifySentinel2Trace(fetch: FetchFn, id: string): Promise<Sentinel2VerificationResponse> {
  const response = await fetch(`${API_BASE}/sentinel-2/${id}/verify-trace`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Trace verification failed: HTTP ${response.status}`)
  }
  return response.json()
}

export async function verifySentinel2Files(
  fetch: FetchFn,
  id: string,
  files: Array<{ filename: string; hashHex: string }>,
): Promise<Sentinel2HashCheckResponse> {
  const response = await fetch(`${API_BASE}/sentinel-2/${id}/verify-files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ files }),
  })
  if (!response.ok) {
    throw new Error(`Sentinel-2 verification failed: HTTP ${response.status}`)
  }
  return response.json()
}
