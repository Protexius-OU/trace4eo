import { useState, useRef, useEffect } from 'react'
import './RecordGraphPage.css'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchGraph, fetchRecord, verifyRecord, verifyFileHashes, verifySentinel2Trace, verifySentinel2File, verifySentinel2Files } from '../api/provenanceApi'
import type { VerificationResult, FileVerificationResponse, PredecessorFileResult, Sentinel2VerificationResponse, Sentinel2FileVerificationResponse, Sentinel2DirectoryVerificationResponse } from '../types/provenance'
import { hashFile, hashFileBlake3Hex } from '../utils/hashFiles'
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
  const [isVerifyingTrace, setIsVerifyingTrace] = useState(false)
  const [traceVerificationResult, setTraceVerificationResult] = useState<Sentinel2VerificationResponse | null>(null)
  const [traceVerificationError, setTraceVerificationError] = useState<string | null>(null)
  const [traceFileStatus, setTraceFileStatus] = useState<string | null>(null)
  const [traceFileResult, setTraceFileResult] = useState<Sentinel2FileVerificationResponse | null>(null)
  const [traceFileError, setTraceFileError] = useState<string | null>(null)
  const [traceDirStatus, setTraceDirStatus] = useState<string | null>(null)
  const [traceDirResult, setTraceDirResult] = useState<Sentinel2DirectoryVerificationResponse | null>(null)
  const [traceDirError, setTraceDirError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const traceFileInputRef = useRef<HTMLInputElement>(null)
  const traceDirInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setVerificationResult(null)
    setFileVerificationResponse(null)
    setPredecessorFileResults([])
    setIsSearchingPredecessors(false)
    setTraceVerificationResult(null)
    setTraceVerificationError(null)
    setTraceFileStatus(null)
    setTraceFileResult(null)
    setTraceFileError(null)
    setTraceDirStatus(null)
    setTraceDirResult(null)
    setTraceDirError(null)
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

  const handleVerifyTrace = async () => {
    if (!id) return
    setIsVerifyingTrace(true)
    setTraceVerificationResult(null)
    setTraceVerificationError(null)
    try {
      const result = await verifySentinel2Trace(id)
      setTraceVerificationResult(result)
    } catch (err) {
      setTraceVerificationError(err instanceof Error ? err.message : 'Trace verification failed')
    } finally {
      setIsVerifyingTrace(false)
    }
  }

  const isSentinel2Record = recordData?.metadata?.dataType?.toLowerCase() === 'sentinel-2'

  const handleVerifyTraceFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !id) return

    setTraceFileResult(null)
    setTraceFileError(null)
    try {
      setTraceFileStatus(`Hashing ${file.name} (${(file.size / 1_048_576).toFixed(1)} MB) with BLAKE3…`)
      const hashHex = await hashFileBlake3Hex(file)
      setTraceFileStatus('Sending hash to server for comparison…')
      const result = await verifySentinel2File(id, file.name, hashHex)
      setTraceFileResult(result)
    } catch (err) {
      setTraceFileError(err instanceof Error ? err.message : 'File verification failed')
    } finally {
      setTraceFileStatus(null)
    }
  }

  const handleVerifyTraceDirectory = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (!files.length || !id) return

    setTraceDirResult(null)
    setTraceDirError(null)
    try {
      const entries: Array<{ filename: string; hashHex: string }> = []
      for (let i = 0; i < files.length; i++) {
        const f = files[i]!
        const sizeMb = (f.size / 1_048_576).toFixed(1)
        setTraceDirStatus(`Hashing file ${i + 1} of ${files.length}: ${f.name} (${sizeMb} MB)`)
        const hashHex = await hashFileBlake3Hex(f)
        entries.push({ filename: f.name, hashHex })
      }
      setTraceDirStatus(`Sending ${entries.length} hashes to server for comparison…`)
      const result = await verifySentinel2Files(id, entries)
      setTraceDirResult(result)
    } catch (err) {
      setTraceDirError(err instanceof Error ? err.message : 'Directory verification failed')
    } finally {
      setTraceDirStatus(null)
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
                disabled={!!traceFileStatus}
                className="btn btn-secondary"
                title="Pick the Sentinel-2 product file (e.g. the .SAFE.zip or any file inside it). The browser hashes it locally with BLAKE3 and compares to the Copernicus registry."
              >
                {traceFileStatus ? 'Working…' : 'Check File Against Copernicus'}
              </button>
              <input
                ref={traceFileInputRef}
                type="file"
                style={{ display: 'none' }}
                onChange={handleVerifyTraceFile}
              />
              <button
                onClick={() => traceDirInputRef.current?.click()}
                disabled={!!traceDirStatus}
                className="btn btn-secondary"
                title="Pick the extracted .SAFE folder. The browser hashes every file with BLAKE3 and compares each to the Copernicus registry. Files not in the registry are reported separately."
              >
                {traceDirStatus ? 'Working…' : 'Check Folder Against Copernicus'}
              </button>
              <input
                ref={traceDirInputRef}
                type="file"
                /* @ts-expect-error -- webkitdirectory is non-standard but supported by all major browsers */
                webkitdirectory=""
                directory=""
                style={{ display: 'none' }}
                onChange={handleVerifyTraceDirectory}
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

      {traceFileStatus && (
        <div className="trace-verification trace-verification-progress">
          <p>{traceFileStatus}</p>
        </div>
      )}

      {(traceFileResult || traceFileError) && (
        <div className={`trace-verification ${traceFileResult?.status === 'OK' ? 'trace-verification-ok' : 'trace-verification-error'}`}>
          {traceFileError ? (
            <>
              <h3 className="trace-verification-title">Could not check the file against Copernicus</h3>
              <p>The verification request did not complete: <code>{traceFileError}</code></p>
            </>
          ) : traceFileResult?.status === 'OK' ? (
            <>
              <h3 className="trace-verification-title">✓ This file is the official Sentinel-2 product</h3>
              <p>
                The BLAKE3 hash of <code>{traceFileResult.filename}</code> exactly matches what
                Copernicus has signed for this product. Combined with the signature on the
                Copernicus record (also verified), this proves the file you supplied is byte-for-byte
                identical to what Copernicus published.
              </p>
              <p className="trace-verification-meta">
                Hash matched: <code>{traceFileResult.providedHash}</code>
                <br />
                Registry reference: <code>{traceFileResult.traceId}</code>
                {traceFileResult.signatureAlgorithm && (
                  <> &middot; Signature: <code>{traceFileResult.signatureAlgorithm}</code></>
                )}
              </p>
            </>
          ) : traceFileResult?.status === 'HASH_MISMATCH' ? (
            <>
              <h3 className="trace-verification-title">✗ File does not match the official product</h3>
              <p>
                A file with the name <code>{traceFileResult.filename}</code> exists in Copernicus's
                registered product, but its BLAKE3 hash differs from what you supplied. This means
                the file you picked is <strong>not</strong> byte-for-byte identical to the official
                Sentinel-2 product, even though the names match.
              </p>
              <p className="trace-verification-meta">
                Your file's hash: <code>{traceFileResult.providedHash}</code>
                <br />
                Copernicus's signed hash: <code>{traceFileResult.expectedHash}</code>
              </p>
              <p className="trace-verification-hint">
                Likely reasons: the file was modified after download, you picked a different version
                of the same product, or the download is corrupted.
              </p>
            </>
          ) : traceFileResult?.status === 'FILE_NOT_IN_TRACE' ? (
            <>
              <h3 className="trace-verification-title">File name not found in the registry</h3>
              <p>
                Copernicus has a record for <code>{traceFileResult.imageId}</code>, but no entry
                inside it matches the file name <code>{traceFileResult.filename}</code>.
              </p>
              <p className="trace-verification-hint">
                Make sure you picked a file from the official .SAFE.zip archive (or the .SAFE.zip
                itself). Files from the GRANULE/IMG_DATA, GRANULE/QI_DATA, AUX_DATA, or top-level
                directories all work — pick one whose basename appears in the official product.
              </p>
            </>
          ) : traceFileResult?.status === 'TRACE_NOT_FOUND' ? (
            <>
              <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
              <p>
                The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
                <code>{traceFileResult.imageId}</code>, so the file cannot be checked against
                anything.
              </p>
            </>
          ) : (
            <>
              <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
              <p>
                A Copernicus record exists for <code>{traceFileResult?.imageId}</code>, but its
                digital signature failed verification, so the file hash comparison was not
                performed.
              </p>
            </>
          )}
        </div>
      )}

      {traceDirStatus && (
        <div className="trace-verification trace-verification-progress">
          <p>{traceDirStatus}</p>
        </div>
      )}

      {(traceDirResult || traceDirError) && (
        <div className={`trace-verification ${
          traceDirError ? 'trace-verification-error' :
          traceDirResult?.traceStatus !== 'OK' ? 'trace-verification-error' :
          (traceDirResult?.mismatchedFiles ?? 0) > 0 ? 'trace-verification-error' :
          (traceDirResult?.matchedFiles ?? 0) === 0 ? 'trace-verification-error' :
          'trace-verification-ok'
        }`}>
          {traceDirError ? (
            <>
              <h3 className="trace-verification-title">Could not check the folder against Copernicus</h3>
              <p>The verification request did not complete: <code>{traceDirError}</code></p>
            </>
          ) : traceDirResult?.traceStatus === 'TRACE_NOT_FOUND' ? (
            <>
              <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
              <p>
                The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
                <code>{traceDirResult.imageId}</code>, so no file in this folder can be checked.
              </p>
            </>
          ) : traceDirResult?.traceStatus === 'SIGNATURE_ERROR' ? (
            <>
              <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
              <p>
                A Copernicus record exists for <code>{traceDirResult.imageId}</code>, but its
                digital signature failed verification, so file hash comparisons were not performed.
              </p>
            </>
          ) : traceDirResult ? (
            <>
              <h3 className="trace-verification-title">
                {traceDirResult.mismatchedFiles === 0 && traceDirResult.matchedFiles > 0
                  ? `✓ Folder matches the official Copernicus product (${traceDirResult.matchedFiles}/${traceDirResult.totalFiles} files matched)`
                  : `✗ Folder does not fully match the official Copernicus product`}
              </h3>
              <p>
                Of <strong>{traceDirResult.totalFiles}</strong> files you supplied:
              </p>
              <ul className="trace-verification-checks">
                <li>
                  <strong>{traceDirResult.matchedFiles}</strong> matched the official BLAKE3 hash in
                  the Copernicus registry.
                </li>
                {traceDirResult.mismatchedFiles > 0 && (
                  <li>
                    <strong>{traceDirResult.mismatchedFiles}</strong> had the right name in the
                    registry but the wrong content (hash mismatch — file was modified or
                    corrupted).
                  </li>
                )}
                {traceDirResult.filesNotInTrace > 0 && (
                  <li>
                    <strong>{traceDirResult.filesNotInTrace}</strong> were not part of the official
                    product. These could be files you added or files outside the .SAFE archive.
                  </li>
                )}
              </ul>
              <details className="trace-verification-details">
                <summary>Per-file results ({traceDirResult.fileResults.length})</summary>
                <table className="trace-verification-table">
                  <thead>
                    <tr><th>File</th><th>Status</th></tr>
                  </thead>
                  <tbody>
                    {traceDirResult.fileResults.map(r => (
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
                Registry reference: <code>{traceDirResult.traceId}</code>
                {traceDirResult.signatureAlgorithm && (
                  <> &middot; Signature: <code>{traceDirResult.signatureAlgorithm}</code></>
                )}
              </p>
            </>
          ) : null}
        </div>
      )}

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
