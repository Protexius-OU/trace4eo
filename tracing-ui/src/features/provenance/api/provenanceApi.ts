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

export async function verifyRecord(id: string): Promise<VerificationResult> {
  const response = await fetch(`${API_BASE}/${id}/verify`, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`Verification failed: ${response.statusText}`)
  }
  return response.json()
}

export type SseEventType = 'oauth_url' | 'complete' | 'error'

export interface SseEvent {
  type: SseEventType
  data: unknown
}

export async function uploadFileWithSse(
  file: File,
  dataType: string,
  dataId: string,
  predecessors: string[] | undefined,
  onEvent: (event: SseEvent) => void
): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dataType', dataType)
  formData.append('dataId', dataId)
  if (predecessors) {
    predecessors.forEach(id => formData.append('predecessors', id))
  }

  // Use direct backend URL to bypass Vite proxy buffering for SSE
  const isDev = window.location.port === '3000'
  const sseUrl = isDev
    ? 'http://localhost:8080/api/provenance/upload/stream'
    : `${API_BASE}/upload/stream`
  const response = await fetch(sseUrl, {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.statusText}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''
  let currentEventType: SseEventType | null = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // Process complete events (separated by double newlines)
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const eventBlock of events) {
      if (!eventBlock.trim()) continue

      const lines = eventBlock.split('\n')

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEventType = line.slice(6).trim() as SseEventType
        } else if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim()
          if (currentEventType && dataStr) {
            try {
              const data = JSON.parse(dataStr)
              onEvent({ type: currentEventType, data })
            } catch {
              // Ignore parse errors
            }
          }
        }
      }
      currentEventType = null
    }
  }
}
