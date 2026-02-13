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

      {signatureDetails && (
        <div className="signature-info">
          <div className="signature-info-title">Signature Information</div>
          <div className="signature-info-details">
            <div><strong>Signed by:</strong> {signatureDetails.signerIdentity}</div>
            <div><strong>Signed at:</strong> {new Date(signatureDetails.signingTime).toLocaleString()}</div>
            <div><strong>Certificate issuer:</strong> {signatureDetails.certificateIssuer}</div>
            <div>
              <strong>Transparency log:</strong>{' '}
              <a
                href={`https://search.sigstore.dev/?logIndex=${signatureDetails.rekorLogIndex}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                View on Rekor (index {signatureDetails.rekorLogIndex})
              </a>
            </div>
          </div>
        </div>
      )}

      {verificationResult && (
        <div className={`verification-result ${verificationResult.status ? 'success' : 'failure'}`}>
          <div className="verification-result-title">
            {verificationResult.status ? (
              <span className="verification-success">✓ Verification successful</span>
            ) : (
              <span className="verification-failure">✗ Verification failed</span>
            )}
          </div>
          {verificationResult.steps && verificationResult.steps.length > 0 && (
            <div className="verification-steps">
              {verificationResult.steps.map((step, index) => (
                <div key={index} className="verification-step">
                  <div className="verification-step-content">
                    <span className={step.status ? 'verification-success' : 'verification-failure'}>
                      {step.status ? '✓' : '✗'}
                    </span>
                    <span>{step.description}</span>
                  </div>
                  {step.errorMessage && (
                    <div className="verification-step-error">
                      {step.errorMessage}
                    </div>
                  )}
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
