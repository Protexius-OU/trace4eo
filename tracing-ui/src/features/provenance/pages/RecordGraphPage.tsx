import { useState, useEffect } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord } from '../api/provenanceApi'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { useRecordVerification } from '../hooks/useRecordVerification'
import { useSentinel2Verification } from '../hooks/useSentinel2Verification'
import Sentinel2TraceVerificationResult from '../components/Sentinel2TraceVerificationResult'
import Sentinel2FileCheckResult from '../components/Sentinel2FileCheckResult'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'
import ProvenanceChainList from '../components/ProvenanceChainList'
import IntegrityChain from '../components/IntegrityChain'

type ChainView = 'graph' | 'list'
const CHAIN_VIEW_STORAGE_KEY = 'provenance-chain-view'

export default function RecordGraphPage() {
  const authFetch = useAuthFetch()
  const { id } = useParams<{ id: string }>()

  const [chainView, setChainView] = useState<ChainView>(() => {
    if (typeof window === 'undefined') return 'graph'
    const saved = window.localStorage.getItem(CHAIN_VIEW_STORAGE_KEY)
    return saved === 'list' ? 'list' : 'graph'
  })

  useEffect(() => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(CHAIN_VIEW_STORAGE_KEY, chainView)
  }, [chainView])

  const { data: graphData, isLoading: graphLoading, error: graphError } = useQuery({
    queryKey: ['graph', id],
    queryFn: () => fetchGraph(authFetch, id!),
    enabled: !!id,
  })

  const { data: recordData } = useQuery({
    queryKey: ['record', id],
    queryFn: () => fetchRecord(authFetch, id!),
    enabled: !!id,
  })

  const {
    state: rv,
    fileInputRef,
    handleVerify,
    handleVerifyFiles,
  } = useRecordVerification(id, graphData, recordData)

  const {
    state: s2,
    traceFileInputRef,
    handleVerifyTrace,
    handleVerifyTraceFile,
  } = useSentinel2Verification(id)

  const isSentinel2Record = recordData?.metadata?.dataType?.toLowerCase() === 'sentinel-2'

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
            disabled={rv.isVerifying}
            className="btn btn-primary"
          >
            {rv.isVerifying ? 'Verifying...' : 'Verify'}
          </button>
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={rv.isVerifyingFiles || !recordData}
            className="btn btn-secondary"
          >
            {rv.isSearchingPredecessors ? 'Searching...' : rv.isVerifyingFiles ? 'Hashing...' : 'Verify Files'}
          </button>
          {isSentinel2Record && (
            <>
              <button
                onClick={handleVerifyTrace}
                disabled={s2.isVerifyingTrace}
                className="btn btn-secondary"
              >
                {s2.isVerifyingTrace ? 'Verifying Trace...' : 'Verify Trace'}
              </button>
              <button
                onClick={() => traceFileInputRef.current?.click()}
                disabled={!!s2.traceCheckStatus}
                className="btn btn-secondary"
                title="Pick one or more files from the Sentinel-2 product. Each is hashed locally and compared to the Copernicus registry."
              >
                {s2.traceCheckStatus ? 'Working…' : 'Check Against Copernicus'}
              </button>
              <input
                ref={traceFileInputRef}
                type="file"
                multiple
                style={{ display: 'none' }}
                onChange={handleVerifyTraceFile}
              />
            </>
          )}
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

      {(s2.traceVerificationResult || s2.traceVerificationError) && (
        <Sentinel2TraceVerificationResult result={s2.traceVerificationResult} error={s2.traceVerificationError} />
      )}

      {s2.traceCheckStatus && (
        <div className="trace-verification trace-verification-progress">
          <p>{s2.traceCheckStatus}</p>
        </div>
      )}

      {(s2.traceCheckResult || s2.traceCheckError) && (
        <Sentinel2FileCheckResult result={s2.traceCheckResult} error={s2.traceCheckError} />
      )}

      {recordData && (
        <IntegrityChain
          record={recordData}
          verificationResult={rv.verificationResult}
          fileVerificationResponse={rv.fileVerificationResponse}
          predecessorFileResults={rv.predecessorFileResults}
          isSearchingPredecessors={rv.isSearchingPredecessors}
        />
      )}

      {graphLoading && <p className="loading">Loading graph...</p>}

      {graphError && <p className="error">Error loading graph: {String(graphError)}</p>}

      {graphData && (
        <>
          <div className="chain-view-toggle">
            <button
              type="button"
              className={`chain-view-toggle-btn${chainView === 'graph' ? ' active' : ''}`}
              onClick={() => setChainView('graph')}
            >
              Graph
            </button>
            <button
              type="button"
              className={`chain-view-toggle-btn${chainView === 'list' ? ' active' : ''}`}
              onClick={() => setChainView('list')}
            >
              List
            </button>
          </div>
          {chainView === 'graph'
            ? <ProvenanceGraphViewer graph={graphData} />
            : <ProvenanceChainList graph={graphData} />}
        </>
      )}
    </div>
  )
}
