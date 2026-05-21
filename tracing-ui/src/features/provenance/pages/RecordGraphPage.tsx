import { useState, useRef, useEffect } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord, verifyFileHashes, verifySentinel2Trace, verifySentinel2Files } from '../api/provenanceApi'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import type { VerificationResult, FileVerificationResponse, PredecessorFileResult, Sentinel2VerificationResponse, Sentinel2HashCheckResponse, Sentinel2HashCheckFileStatus } from '../types/provenance'
import { hashFile, hashFileBlake3Hex } from '../utils/hashFiles'
import ProvenanceGraphViewer from '../components/ProvenanceGraphViewer'
import ProvenanceChainList from '../components/ProvenanceChainList'
import IntegrityChain from '../components/IntegrityChain'

type ChainView = 'graph' | 'list'
const CHAIN_VIEW_STORAGE_KEY = 'provenance-chain-view'

export default function RecordGraphPage() {
  const authFetch = useAuthFetch()
  const { id } = useParams<{ id: string }>()
  const [isVerifying, setIsVerifying] = useState(false)
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null)
  const [isVerifyingFiles, setIsVerifyingFiles] = useState(false)
  const [fileVerificationResponse, setFileVerificationResponse] = useState<FileVerificationResponse | null>(null)
  const [predecessorFileResults, setPredecessorFileResults] = useState<PredecessorFileResult[]>([])
  const [isSearchingPredecessors, setIsSearchingPredecessors] = useState(false)
  const [isVerifyingTrace, setIsVerifyingTrace] = useState(false)
  const [traceVerificationResult, setTraceVerificationResult] = useState<Sentinel2VerificationResponse | null>(null)
  const [chainView, setChainView] = useState<ChainView>(() => {
    if (typeof window === 'undefined') return 'graph'
    const saved = window.localStorage.getItem(CHAIN_VIEW_STORAGE_KEY)
    return saved === 'list' ? 'list' : 'graph'
  })

  useEffect(() => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(CHAIN_VIEW_STORAGE_KEY, chainView)
  }, [chainView])
  const [traceVerificationError, setTraceVerificationError] = useState<string | null>(null)
  const [traceCheckStatus, setTraceCheckStatus] = useState<string | null>(null)
  const [traceCheckResult, setTraceCheckResult] = useState<Sentinel2HashCheckResponse | null>(null)
  const [traceCheckError, setTraceCheckError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const traceFileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setVerificationResult(null)
    setFileVerificationResponse(null)
    setPredecessorFileResults([])
    setIsSearchingPredecessors(false)
    setTraceVerificationResult(null)
    setTraceVerificationError(null)
    setTraceCheckStatus(null)
    setTraceCheckResult(null)
    setTraceCheckError(null)
  }, [id])

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

  const handleVerify = async () => {
    if (!id) return
    setIsVerifying(true)
    setVerificationResult(null)
    try {
      const result = await verifyRecord(authFetch, id)
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

  const handleVerifyTrace = async () => {
    if (!id) return
    setIsVerifyingTrace(true)
    setTraceVerificationResult(null)
    setTraceVerificationError(null)
    try {
      const result = await verifySentinel2Trace(authFetch, id)
      setTraceVerificationResult(result)
    } catch (err) {
      setTraceVerificationError(err instanceof Error ? err.message : 'Trace verification failed')
    } finally {
      setIsVerifyingTrace(false)
    }
  }

  const isSentinel2Record = recordData?.metadata?.dataType?.toLowerCase() === 'sentinel-2'

  const handleVerifyTraceFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (!files.length || !id) return

    setTraceCheckResult(null)
    setTraceCheckError(null)
    try {
      const entries: Array<{ filename: string; hashHex: string }> = []
      for (let i = 0; i < files.length; i++) {
        const f = files[i]!
        const sizeMb = (f.size / 1_048_576).toFixed(1)
        const prefix = files.length === 1 ? '' : `File ${i + 1} of ${files.length}: `
        setTraceCheckStatus(`${prefix}Hashing ${f.name} (${sizeMb} MB)…`)
        const hashHex = await hashFileBlake3Hex(f)
        entries.push({ filename: f.name, hashHex })
      }
      setTraceCheckStatus(`Sending ${entries.length === 1 ? 'hash' : `${entries.length} hashes`} to server for comparison…`)
      const result = await verifySentinel2Files(authFetch, id, entries)
      setTraceCheckResult(result)
    } catch (err) {
      setTraceCheckError(err instanceof Error ? err.message : 'Verification failed')
    } finally {
      setTraceCheckStatus(null)
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
      const result = await verifyFileHashes(authFetch, id, inputs)
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
            const nodeResult = await verifyFileHashes(authFetch, node.id, [...remaining.values()])
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
          {isSentinel2Record && (
            <>
              <button
                onClick={handleVerifyTrace}
                disabled={isVerifyingTrace}
                className="btn btn-secondary"
              >
                {isVerifyingTrace ? 'Verifying Trace...' : 'Verify Trace'}
              </button>
              <button
                onClick={() => traceFileInputRef.current?.click()}
                disabled={!!traceCheckStatus}
                className="btn btn-secondary"
                title="Pick one or more files from the Sentinel-2 product. Each is hashed locally and compared to the Copernicus registry."
              >
                {traceCheckStatus ? 'Working…' : 'Check Against Copernicus'}
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

      {(traceVerificationResult || traceVerificationError) && (
        <div className={`trace-verification ${traceVerificationResult?.status === 'OK' ? 'trace-verification-ok' : 'trace-verification-error'}`}>
          {traceVerificationError ? (
            <>
              <h3 className="trace-verification-title">Could not check the Copernicus registry</h3>
              <p>The verification request did not complete: <code>{traceVerificationError}</code></p>
              <p className="trace-verification-hint">
                This usually means the Copernicus Traceability service is temporarily unavailable
                or the backend could not reach it. Try again in a few minutes.
              </p>
            </>
          ) : traceVerificationResult?.status === 'OK' ? (
            <>
              <h3 className="trace-verification-title">Product name found in the Copernicus registry</h3>
              <p>
                The data ID in this provenance record matches a real Sentinel-2 product registered
                with the{' '}
                <a
                  href="https://documentation.dataspace.copernicus.eu/APIs/Traceability.html"
                  target="_blank"
                  rel="noreferrer"
                >Copernicus Data Space Ecosystem</a>
                , and Copernicus's signed record of that product is intact.
              </p>
              <p className="trace-verification-warning">
                <strong>This does not prove the file behind this record is the real product.</strong>{' '}
                The data ID is a string supplied at signing time and isn't compared against any
                file content. To prove the file matches, hash the original product file and check
                it against the registry below, or run the verification CLI against the file on
                disk.
              </p>
              <p className="trace-verification-meta">
                Registry reference: <code>{traceVerificationResult.traceId}</code>
                {traceVerificationResult.signatureAlgorithm && (
                  <> &middot; Signature algorithm: <code>{traceVerificationResult.signatureAlgorithm}</code></>
                )}
                {traceVerificationResult.hashAlgorithm && (
                  <> &middot; File hash algorithm used by Copernicus: <code>{traceVerificationResult.hashAlgorithm}</code></>
                )}
              </p>
            </>
          ) : traceVerificationResult?.status === 'TRACE_NOT_FOUND' ? (
            <>
              <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
              <p>
                The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
                <code>{traceVerificationResult.imageId}</code>.
              </p>
              <p className="trace-verification-hint">
                Common reasons: the data identifier doesn't exactly match a real Sentinel-2 product
                name (a typo when the record was signed), the product predates the Copernicus
                traceability service (records began in mid-April 2024 for newly-ingested products),
                or this provenance record was created from data that isn't actually a Sentinel-2
                product.
              </p>
            </>
          ) : (
            <>
              <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
              <p>
                A Copernicus registry record exists for{' '}
                <code>{traceVerificationResult?.imageId}</code>, but its digital signature failed
                verification.
              </p>
              <p className="trace-verification-hint">
                This is unusual. It can indicate a temporary problem at the Copernicus service, a
                certificate rotation that this client doesn't yet understand, or &mdash; least
                likely &mdash; that the registry entry has been tampered with.
              </p>
            </>
          )}
        </div>
      )}

      {traceCheckStatus && (
        <div className="trace-verification trace-verification-progress">
          <p>{traceCheckStatus}</p>
        </div>
      )}

      {(traceCheckResult || traceCheckError) && (() => {
        const isMulti = (traceCheckResult?.fileResults.length ?? 0) > 1
        const fileResult = traceCheckResult?.fileResults[0]
        const status: Sentinel2HashCheckFileStatus | undefined = fileResult?.status
        const allMatched = traceCheckResult
          ? traceCheckResult.matchedFiles === traceCheckResult.totalFiles
          : false
        return (
          <div className={`trace-verification ${
            traceCheckError ? 'trace-verification-error' :
            traceCheckResult?.traceStatus !== 'OK' ? 'trace-verification-error' :
            isMulti ? (allMatched ? 'trace-verification-ok' : 'trace-verification-error') :
            status === 'OK' ? 'trace-verification-ok' :
            'trace-verification-error'
          }`}>
            {traceCheckError ? (
              <>
                <h3 className="trace-verification-title">Could not check against Copernicus</h3>
                <p>The verification request did not complete: <code>{traceCheckError}</code></p>
              </>
            ) : traceCheckResult?.traceStatus === 'TRACE_NOT_FOUND' ? (
              <>
                <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
                <p>
                  The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
                  <code>{traceCheckResult.imageId}</code>, so nothing can be checked.
                </p>
              </>
            ) : traceCheckResult?.traceStatus === 'SIGNATURE_ERROR' ? (
              <>
                <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
                <p>
                  A Copernicus record exists for <code>{traceCheckResult.imageId}</code>, but its
                  digital signature failed verification, so file hash comparisons were not performed.
                </p>
              </>
            ) : isMulti && traceCheckResult ? (
              <>
                <h3 className="trace-verification-title">
                  {allMatched
                    ? `✓ All ${traceCheckResult.totalFiles} files match the official Copernicus product`
                    : `✗ Some files do not match the official Copernicus product`}
                </h3>
                <p>Of <strong>{traceCheckResult.totalFiles}</strong> files you supplied:</p>
                <ul className="trace-verification-checks">
                  <li>
                    <strong>{traceCheckResult.matchedFiles}</strong> matched the official hash in
                    the Copernicus registry.
                  </li>
                  {traceCheckResult.mismatchedFiles > 0 && (
                    <li>
                      <strong>{traceCheckResult.mismatchedFiles}</strong> had the right name in the
                      registry but the wrong content (hash mismatch — file was modified or corrupted).
                    </li>
                  )}
                  {traceCheckResult.filesNotInTrace > 0 && (
                    <li>
                      <strong>{traceCheckResult.filesNotInTrace}</strong> were not in Copernicus's
                      signed file list. Copernicus doesn't sign every file in a product, and the
                      exact set varies by product type — so this isn't automatically a tampering
                      signal.
                    </li>
                  )}
                </ul>
                <details className="trace-verification-details">
                  <summary>Per-file results ({traceCheckResult.fileResults.length})</summary>
                  <table className="trace-verification-table">
                    <thead><tr><th>File</th><th>Status</th></tr></thead>
                    <tbody>
                      {traceCheckResult.fileResults.map(r => (
                        <tr key={r.filename} className={`trace-verification-row-${r.status.toLowerCase()}`}>
                          <td><code>{r.filename}</code></td>
                          <td>
                            {r.status === 'OK' ? '✓ matched' :
                             r.status === 'HASH_MISMATCH' ? '✗ hash mismatch' :
                             '○ not in registry'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </details>
                <p className="trace-verification-meta">
                  Registry reference: <code>{traceCheckResult.traceId}</code>
                  {traceCheckResult.signatureAlgorithm && (
                    <> &middot; Signature: <code>{traceCheckResult.signatureAlgorithm}</code></>
                  )}
                </p>
              </>
            ) : status === 'OK' && fileResult ? (
              <>
                <h3 className="trace-verification-title">✓ This file is the official Sentinel-2 product</h3>
                <p>
                  The hash of <code>{fileResult.filename}</code> exactly matches what
                  Copernicus has signed for this product. Combined with the signature on the
                  Copernicus record (also verified), this proves the file you supplied is
                  byte-for-byte identical to what Copernicus published.
                </p>
                <p className="trace-verification-meta">
                  Hash matched: <code>{fileResult.providedHash}</code>
                  <br />
                  Registry reference: <code>{traceCheckResult?.traceId}</code>
                  {traceCheckResult?.signatureAlgorithm && (
                    <> &middot; Signature: <code>{traceCheckResult.signatureAlgorithm}</code></>
                  )}
                </p>
              </>
            ) : status === 'HASH_MISMATCH' && fileResult ? (
              <>
                <h3 className="trace-verification-title">✗ File does not match the official product</h3>
                <p>
                  A file named <code>{fileResult.filename}</code> exists in Copernicus's signed
                  product, but its hash differs from what you supplied. This means the file
                  you picked is <strong>not</strong> byte-for-byte identical to the official
                  Sentinel-2 product, even though the names match.
                </p>
                <p className="trace-verification-meta">
                  Your file's hash: <code>{fileResult.providedHash}</code>
                  <br />
                  Copernicus's signed hash: <code>{fileResult.expectedHash}</code>
                </p>
                <p className="trace-verification-hint">
                  Likely reasons: the file was modified after download, you picked a different
                  version of the same product, or the download is corrupted.
                </p>
              </>
            ) : status === 'FILE_NOT_IN_TRACE' && fileResult ? (
              <>
                <h3 className="trace-verification-title">File not found in Copernicus's signed file list</h3>
                <p>
                  Copernicus has a record for <code>{traceCheckResult?.imageId}</code>, but no entry
                  in its signed contents matches the file name <code>{fileResult.filename}</code>.
                </p>
                <p className="trace-verification-hint">
                  Copernicus doesn't sign every file in a product, and the exact set varies by
                  product type — so this isn't automatically a tampering signal. Try a different
                  file from the product.
                </p>
              </>
            ) : null}
          </div>
        )
      })()}

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
