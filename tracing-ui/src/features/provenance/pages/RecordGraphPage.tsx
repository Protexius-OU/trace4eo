import { useState, useRef, useEffect } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord, verifyFileHashes } from '../api/provenanceApi'
import type { VerificationResult, FileVerificationResponse, PredecessorFileResult } from '../types/provenance'
import { hashFile } from '../utils/hashFiles'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'
import IntegrityChain from '../components/IntegrityChain'

export default function RecordGraphPage() {
  const { id } = useParams<{ id: string }>()
  const [isVerifying, setIsVerifying] = useState(false)
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null)
  const [isVerifyingFiles, setIsVerifyingFiles] = useState(false)
  const [fileVerificationResponse, setFileVerificationResponse] = useState<FileVerificationResponse | null>(null)
  const [predecessorFileResults, setPredecessorFileResults] = useState<PredecessorFileResult[]>([])
  const [isSearchingPredecessors, setIsSearchingPredecessors] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setVerificationResult(null)
    setFileVerificationResponse(null)
    setPredecessorFileResults([])
    setIsSearchingPredecessors(false)
  }, [id])

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
    setPredecessorFileResults([])
    setIsSearchingPredecessors(false)
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

      const notFound = result.fileResults.filter(r => r.status === 'NOT_IN_RECORD')
      if (notFound.length > 0 && graphData) {
        setIsSearchingPredecessors(true)
        const predecessors = graphData.nodes
          .filter(n => n.id !== id)
          .sort((a, b) => a.depth - b.depth)
        const remaining = new Map(
          notFound.map(r => [r.filename, inputs.find(i => i.filename === r.filename)!])
        )
        const found: PredecessorFileResult[] = []
        for (const node of predecessors) {
          if (remaining.size === 0) break
          try {
            const nodeResult = await verifyFileHashes(node.id, [...remaining.values()])
            for (const fr of nodeResult.fileResults) {
              if ((fr.status === 'MATCHED' || fr.status === 'MISMATCH') && remaining.has(fr.filename)) {
                found.push({
                  filename: fr.filename,
                  foundInRecordId: node.id,
                  foundAtDepth: node.depth,
                  status: fr.status,
                  recordDataId: node.dataId,
                  recordDataType: node.dataType,
                })
                remaining.delete(fr.filename)
              }
            }
          } catch (err) {
            console.warn(`Skipping predecessor ${node.id} during file search:`, err)
          }
        }
        setPredecessorFileResults(found)
        setIsSearchingPredecessors(false)
      }
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
            {isSearchingPredecessors ? 'Searching...' : isVerifyingFiles ? 'Hashing...' : 'Verify Files'}
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
          predecessorFileResults={predecessorFileResults}
          isSearchingPredecessors={isSearchingPredecessors}
        />
      )}

      {graphLoading && <p className="loading">Loading graph...</p>}

      {graphError && <p className="error">Error loading graph: {String(graphError)}</p>}

      {graphData && <ProvenanceGraphViewer graph={graphData} />}
    </div>
  )
}
