import type { ProvenanceRecord, ProvenanceGraph, PagedResponse, RecordFilters, VerificationResult } from '../types/provenance'

const API_BASE = '/api/provenance'

export async function fetchRecords(
  page: number = 0,
  size: number = 20,
  filters: RecordFilters = {}
): Promise<PagedResponse<ProvenanceRecord>> {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  })

  if (filters.dataType) params.set('dataType', filters.dataType)
  if (filters.dataId) params.set('dataId', filters.dataId)
  if (filters.fromDate) params.set('fromDate', filters.fromDate)
  if (filters.toDate) params.set('toDate', filters.toDate)

  const response = await fetch(`${API_BASE}?${params}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch records: ${response.statusText}`)
  }
  return response.json()
}

export async function fetchRecord(id: string): Promise<ProvenanceRecord> {
  const response = await fetch(`${API_BASE}/${id}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch record: ${response.statusText}`)
  }
  return response.json()
}

export async function fetchGraph(id: string): Promise<ProvenanceGraph> {
  const response = await fetch(`${API_BASE}/${id}/graph`)
  if (!response.ok) {
    throw new Error(`Failed to fetch graph: ${response.statusText}`)
  }
  return response.json()
}

export async function downloadZip(id: string): Promise<void> {
  const response = await fetch(`${API_BASE}/${id}/zip`)
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

  const response = await fetch(`${API_BASE}/upload`, {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.statusText}`)
  }
  return response.json()
}

export async function verifyRecord(id: string): Promise<VerificationResult> {
  const response = await fetch(`${API_BASE}/${id}/verify`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Verification failed: ${response.statusText}`)
  }
  return response.json()
}
