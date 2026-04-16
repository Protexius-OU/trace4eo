import { useState, useRef } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord, verifyFileHashes } from '../api/provenanceApi'
import type { VerificationResult, FileVerificationResponse } from '../types/provenance'
import { hashFile } from '../utils/hashFiles'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'
import IntegrityChain from '../components/IntegrityChain'

export default function RecordGraphPage() {
  const { id } = useParams<{ id: string }>()
  const [isVerifying, setIsVerifying] = useState(false)
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null)
  const [isVerifyingFiles, setIsVerifyingFiles] = useState(false)
  const [fileVerificationResponse, setFileVerificationResponse] = useState<FileVerificationResponse | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  const handleVerifyFiles = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    if (files.length === 0 || !id) return

    setIsVerifyingFiles(true)
    setFileVerificationResponse(null)
    try {
      const inputs = await Promise.all(files.map(async file => {
        const recordFile = recordData?.filesInfo?.files.find(
          f => f.path === file.name || f.path?.endsWith('/' + file.name)
        )
        const algorithm = recordFile?.hashAlgorithm ?? 'SHA256'
        const hashValue = await hashFile(file, algorithm)
        return { filename: file.name, hashValue }
      }))
      const result = await verifyFileHashes(id, inputs)
      setFileVerificationResponse(result)
    } catch (err) {
      setFileVerificationResponse({
        status: false,
        error: 'UNKNOWN_ERROR',
        errorMessage: err instanceof Error ? err.message : 'File verification failed',
        steps: [],
        fileResults: [],
      })
    } finally {
      setIsVerifyingFiles(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
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
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={isVerifyingFiles || !recordData}
            className="btn btn-secondary"
          >
            {isVerifyingFiles ? 'Hashing...' : 'Verify Files'}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            style={{ display: 'none' }}
            onChange={handleVerifyFiles}
          />
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
          fileVerificationResponse={fileVerificationResponse}
        />
      )}

      {graphLoading && <p className="loading">Loading graph...</p>}

      {graphError && <p className="error">Error loading graph: {String(graphError)}</p>}

      {graphData && <ProvenanceGraphViewer graph={graphData} />}
    </div>
  )
}
