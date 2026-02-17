import { authFetch } from '../../../core/auth/authFetch'
import type { ProvenanceRecord, ProvenanceGraph, PagedResponse, RecordFilters, FilterOptions, VerificationResult } from '../types/provenance'

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

export async function uploadFile(
  file: File,
  dataType: string,
  dataId: string,
  predecessors?: string[]
): Promise<ProvenanceRecord> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dataType', dataType)
  formData.append('dataId', dataId)
  if (predecessors) {
    predecessors.forEach(id => formData.append('predecessors', id))
  }

  const response = await authFetch(`${API_BASE}/upload`, {
    method: 'POST',
    body: formData,
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Upload failed: ${response.statusText}`)
  }
  return response.json()
}
