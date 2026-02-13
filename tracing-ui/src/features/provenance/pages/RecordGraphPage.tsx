import { useState } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord } from '../api/provenanceApi'
import type { VerificationResult } from '../types/provenance'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'
import IntegrityChain from '../components/IntegrityChain'

export default function RecordGraphPage() {
  const { id } = useParams<{ id: string }>()
  const [isVerifying, setIsVerifying] = useState(false)
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null)

  const { data: graphData, isLoading: graphLoading, error: graphError } = useQuery({
    queryKey: ['graph', id],
    queryFn: () => fetchGraph(id!),
    enabled: !!id,
  })

  const { data: recordData } = useQuery({
    queryKey: ['record', id],
    queryFn: () => fetchRecord(id!),
    enabled: !!id,
  })

  const handleVerify = async () => {
    if (!id) return
    setIsVerifying(true)
    setVerificationResult(null)
    try {
      const result = await verifyRecord(id)
      setVerificationResult(result)
    } catch (err) {
      setVerificationResult({
        status: false,
        error: 'UNKNOWN_ERROR',
        errorMessage: err instanceof Error ? err.message : 'Verification failed',
        steps: []
      })
    } finally {
      setIsVerifying(false)
    }
  }

  if (!id) {
    return <p className="error">No record ID provided</p>
  }

  return (
    <div>
      <div className="record-header">
        <h1>Provenance Record Details</h1>
        <div className="record-header-actions">
          <button
            onClick={handleVerify}
            disabled={isVerifying}
            className="btn btn-primary"
          >
            {isVerifying ? 'Verifying...' : 'Verify'}
          </button>
          <Link to="/" className="btn btn-secondary">Back to List</Link>
        </div>
      </div>

      <p className="record-id">
        Record ID: <span className="uuid">{id}</span>
      </p>

      {recordData && (
        <IntegrityChain
          record={recordData}
          verificationResult={verificationResult}
        />
      )}

      {graphLoading && <p className="loading">Loading graph...</p>}

      {graphError && <p className="error">Error loading graph: {String(graphError)}</p>}

      {graphData && <ProvenanceGraphViewer graph={graphData} />}
    </div>
  )
}
