import { useState } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord } from '../api/provenanceApi'
import type { VerificationResult } from '../types/provenance'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'

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

  const signatureDetails = recordData?.signature?.details

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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h1>Provenance Record Details</h1>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
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

      <p style={{ color: '#666', fontSize: '0.875rem', marginBottom: '1rem' }}>
        Record ID: <span className="uuid">{id}</span>
      </p>

      {signatureDetails && (
        <div style={{
          padding: '1rem',
          marginBottom: '1rem',
          borderRadius: '4px',
          background: '#f5f5f5',
          border: '1px solid #e0e0e0'
        }}>
          <div style={{ fontWeight: 'bold', marginBottom: '0.5rem' }}>Signature Information</div>
          <div style={{ fontSize: '0.875rem', display: 'grid', gap: '0.25rem' }}>
            <div><strong>Signed by:</strong> {signatureDetails.signerIdentity}</div>
            <div><strong>Authenticated via:</strong> {signatureDetails.oidcIssuer}</div>
            <div><strong>Signed at:</strong> {new Date(signatureDetails.signingTime).toLocaleString()}</div>
            <div><strong>Certificate issuer:</strong> {signatureDetails.certificateIssuer}</div>
            <div>
              <strong>Transparency log:</strong>{' '}
              <a
                href={`https://search.sigstore.dev/?logIndex=${signatureDetails.rekorLogIndex}`}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: '#1565c0' }}
              >
                View on Rekor (index {signatureDetails.rekorLogIndex})
              </a>
            </div>
          </div>
        </div>
      )}

      {verificationResult && (
        <div style={{
          padding: '1rem',
          marginBottom: '1rem',
          borderRadius: '4px',
          background: verificationResult.status ? '#e8f5e9' : '#ffebee',
          border: `1px solid ${verificationResult.status ? '#a5d6a7' : '#ef9a9a'}`
        }}>
          <div style={{ fontWeight: 'bold', marginBottom: '0.5rem' }}>
            {verificationResult.status ? (
              <span style={{ color: '#2e7d32' }}>✓ Verification successful</span>
            ) : (
              <span style={{ color: '#c62828' }}>✗ Verification failed</span>
            )}
          </div>
          {verificationResult.steps && verificationResult.steps.length > 0 && (
            <div style={{ fontSize: '0.875rem' }}>
              {verificationResult.steps.map((step, index) => (
                <div key={index} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
                  <span style={{ color: step.status ? '#2e7d32' : '#c62828' }}>
                    {step.status ? '✓' : '✗'}
                  </span>
                  <span>{step.description}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {graphLoading && <p className="loading">Loading graph...</p>}

      {graphError && <p className="error">Error loading graph: {String(graphError)}</p>}

      {graphData && <ProvenanceGraphViewer graph={graphData} />}
    </div>
  )
}
