import React, { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { Check, X, Minus, HelpCircle } from 'lucide-react'
import type { ProvenanceRecord, VerificationResult, VerificationStepName, FileVerificationResponse, FileHashInfo, PredecessorFileResult } from '../types/provenance'
import './IntegrityChain.css'

const FILE_COLLAPSE_THRESHOLD = 5

interface Props {
  record: ProvenanceRecord
  verificationResult: VerificationResult | null
  fileVerificationResponse?: FileVerificationResponse | null
  predecessorFileResults?: PredecessorFileResult[]
  isSearchingPredecessors?: boolean
}

type Status = 'idle' | 'pass' | 'fail' | 'skipped' | 'unknown'

function findStepStatus(result: VerificationResult | null, name: VerificationStepName): Status {
  const step = result?.steps?.find(s => s.name === name)
  if (!step) return 'idle'
  if (!step.status) return 'fail'
  if (step.description.toLowerCase().includes('skipped')) return 'skipped'
  return 'pass'
}


function connectorClass(status: Status): string {
  if (status === 'pass' || status === 'skipped') return 'pass'
  if (status === 'fail') return 'fail'
  return 'idle'
}

function StatusIcon({ status }: { status: Status }) {
  const props = { size: 14, strokeWidth: 2.5, 'aria-hidden': true as const }
  switch (status) {
    case 'pass':    return <Check       {...props} className="ic-status ic-status-pass" />
    case 'fail':    return <X           {...props} className="ic-status ic-status-fail" />
    case 'skipped': return <Minus       {...props} className="ic-status ic-status-skipped" />
    case 'unknown': return <HelpCircle  {...props} className="ic-status ic-status-unknown" />
    default:        return null
  }
}

function FileRow({ file, status, showHash }: { file: { path: string | null; hashAlgorithm: string; hashValue: string }; status: Status; showHash: boolean }) {
  const isDimmed = status === 'unknown'
  return (
    <div className={`ic-file${isDimmed ? ' ic-file-dimmed' : ''}`}>
      <StatusIcon status={status} />
      <div className="ic-file-info">
        <span className="ic-file-path">{file.path}</span>
        {showHash && (
          <code className="ic-hash-value">{file.hashAlgorithm}: {file.hashValue}</code>
        )}
      </div>
    </div>
  )
}

function ShowMoreButton({ hidden, expanded, onToggle }: { hidden: number; expanded: boolean; onToggle: () => void }) {
  if (hidden <= 0) return null
  return (
    <button className="ic-show-more" onClick={onToggle}>
      {expanded ? 'Show less' : `Show ${hidden} more`}
    </button>
  )
}

interface FilesCollapsedProps {
  files: Array<{ path: string | null; hashAlgorithm: string; hashValue: string }>
  nodeStatus: Status
  expanded: boolean
  setExpanded: (v: boolean) => void
  showHash: boolean
}

function FilesCollapsed({ files, nodeStatus, expanded, setExpanded, showHash }: FilesCollapsedProps) {
  const visible = expanded ? files : files.slice(0, FILE_COLLAPSE_THRESHOLD)
  const hidden = Math.max(0, files.length - FILE_COLLAPSE_THRESHOLD)
  return (
    <>
      {visible.map(f => <FileRow key={f.path} file={f} status={nodeStatus} showHash={showHash} />)}
      <ShowMoreButton hidden={hidden} expanded={expanded} onToggle={() => setExpanded(!expanded)} />
    </>
  )
}

function NotInRecordRow({
  filename,
  predecessorResult,
  isSearching,
}: {
  filename: string
  predecessorResult: PredecessorFileResult | undefined
  isSearching: boolean
}) {
  const topStatus: Status = predecessorResult
    ? predecessorResult.status === 'MATCHED' ? 'pass' : 'fail'
    : 'unknown'

  return (
    <div className="ic-file ic-file-not-in-record">
      <StatusIcon status={topStatus} />
      <div className="ic-file-info">
        <span className="ic-file-path">{filename}</span>
        <span className="ic-note">Not in this record</span>
        {predecessorResult ? (
          <span className={`ic-predecessor-result ic-predecessor-${predecessorResult.status === 'MATCHED' ? 'matched' : 'mismatch'}`}>
            {'↳ '}
            <Link to={`/records/${predecessorResult.foundInRecordId}/graph`}>
              {predecessorResult.recordDataId} ({predecessorResult.recordDataType})
            </Link>
            {' — '}
            <span className="ic-status-inline">
              {predecessorResult.status === 'MATCHED'
                ? <><Check size={12} strokeWidth={2.5} aria-hidden="true" />Matched</>
                : <><X size={12} strokeWidth={2.5} aria-hidden="true" />Mismatch</>}
            </span>
            {` at depth ${predecessorResult.foundAtDepth}`}
          </span>
        ) : isSearching ? (
          <span className="ic-note">Searching predecessors…</span>
        ) : (
          <span className="ic-note">Not found in any predecessor record</span>
        )}
      </div>
    </div>
  )
}

interface FilesWithVerificationProps {
  files: FileHashInfo[]
  fvr: FileVerificationResponse
  uncheckedExpanded: boolean
  setUncheckedExpanded: (v: boolean) => void
  predecessorFileResults: PredecessorFileResult[]
  isSearchingPredecessors: boolean
  showHash: boolean
}

function FilesWithVerification({
  files,
  fvr,
  uncheckedExpanded,
  setUncheckedExpanded,
  predecessorFileResults,
  isSearchingPredecessors,
  showHash,
}: FilesWithVerificationProps) {
  const checkedFiles = files
    .filter(f => fvr.fileResults.some(r => r.recordPath === f.path))
    .sort((a, b) => {
      const ai = fvr.fileResults.findIndex(r => r.recordPath === a.path)
      const bi = fvr.fileResults.findIndex(r => r.recordPath === b.path)
      return ai - bi
    })
  const uncheckedFiles = files.filter(f => !fvr.fileResults.some(r => r.recordPath === f.path))
  const notInRecord = fvr.fileResults.filter(r => r.status === 'NOT_IN_RECORD')

  const visibleUnchecked = uncheckedExpanded
    ? uncheckedFiles
    : uncheckedFiles.slice(0, FILE_COLLAPSE_THRESHOLD)
  const hiddenUnchecked = Math.max(0, uncheckedFiles.length - FILE_COLLAPSE_THRESHOLD)

  return (
    <>
      <div className="ic-file-section">
        {checkedFiles.map(f => {
          const result = fvr.fileResults.find(r => r.recordPath === f.path)!
          const status: Status = result.status === 'MATCHED' ? 'pass' : 'fail'
          return <FileRow key={f.path} file={f} status={status} showHash={showHash} />
        })}
      </div>
      {uncheckedFiles.length > 0 && (
        <div className="ic-file-section">
          {visibleUnchecked.map(f => <FileRow key={f.path} file={f} status="unknown" showHash={showHash} />)}
          <ShowMoreButton
            hidden={hiddenUnchecked}
            expanded={uncheckedExpanded}
            onToggle={() => setUncheckedExpanded(!uncheckedExpanded)}
          />
        </div>
      )}
      {notInRecord.length > 0 && (
        <div className="ic-file-section">
          {notInRecord.map(r => (
            <NotInRecordRow
              key={r.filename}
              filename={r.filename}
              predecessorResult={predecessorFileResults.find(p => p.filename === r.filename)}
              isSearching={isSearchingPredecessors && !predecessorFileResults.some(p => p.filename === r.filename)}
            />
          ))}
        </div>
      )}
    </>
  )
}

function Connector({ label, status }: {
  label: React.ReactNode
  status: Status
}) {
  return (
    <div className={`ic-connector ic-connector-${connectorClass(status)}`}>
      <div className="ic-line" />
      <span className="ic-label">{label}</span>
      <div className="ic-line" />
    </div>
  )
}

const SHOW_CRYPTO_STORAGE_KEY = 'ic-show-crypto'
const MINIFIED_STORAGE_KEY = 'ic-minified'

export default function IntegrityChain({ record, verificationResult, fileVerificationResponse, predecessorFileResults = [], isSearchingPredecessors = false }: Props) {
  const { manifest, filesInfo, metadata, signature } = record
  const details = signature?.details

  const [allFilesExpanded, setAllFilesExpanded] = useState(false)
  const [uncheckedExpanded, setUncheckedExpanded] = useState(false)
  const [showCrypto, setShowCrypto] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false
    return window.localStorage.getItem(SHOW_CRYPTO_STORAGE_KEY) === 'true'
  })
  const [minified, setMinified] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false
    return window.localStorage.getItem(MINIFIED_STORAGE_KEY) === 'true'
  })

  // Crypto details and minified are opposite ends of a density spectrum,
  // so enabling one disables the other.
  function toggleCrypto(checked: boolean) {
    setShowCrypto(checked)
    if (checked) setMinified(false)
  }

  function toggleMinified(checked: boolean) {
    setMinified(checked)
    if (checked) setShowCrypto(false)
  }

  useEffect(() => {
    setAllFilesExpanded(false)
    setUncheckedExpanded(false)
  }, [fileVerificationResponse])

  useEffect(() => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(SHOW_CRYPTO_STORAGE_KEY, String(showCrypto))
  }, [showCrypto])

  useEffect(() => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(MINIFIED_STORAGE_KEY, String(minified))
  }, [minified])

  const verified = verificationResult !== null

  const sigStatus = findStepStatus(verificationResult, 'SIGNATURE')
  const metaHashStatus = findStepStatus(verificationResult, 'METADATA')
  const filesHashStatus = findStepStatus(verificationResult, 'FILES_INFO')
  const fileContentsStatus = findStepStatus(verificationResult, 'FILE_CONTENTS')
  const fileVerificationContentsStatus = fileVerificationResponse
    ? findStepStatus(fileVerificationResponse, 'FILE_CONTENTS')
    : null
  const activeFileContentsStatus = fileVerificationContentsStatus ?? fileContentsStatus
  const filesNodeStatus: Status = activeFileContentsStatus === 'skipped' ? 'idle' : activeFileContentsStatus

  const sigConnectorLabel = details?.manifestHash
    ? (
      <>
        <span>signs manifest hash</span>
        <br />
        <code className="ic-connector-hash">
          {signature?.hashAlgorithm.toUpperCase()}: {details.manifestHash}
        </code>
      </>
    )
    : 'signs manifest hash'

  return (
    <div className={`integrity-chain${minified ? ' ic-minified' : ''}`}>
      <div className="ic-header">
        <span className="ic-title">Integrity Chain</span>
        <div className="ic-header-actions">
          <label className="ic-crypto-toggle">
            <input
              type="checkbox"
              checked={minified}
              onChange={e => toggleMinified(e.target.checked)}
            />
            <span>Minified</span>
          </label>
          <label className="ic-crypto-toggle">
            <input
              type="checkbox"
              checked={showCrypto}
              onChange={e => toggleCrypto(e.target.checked)}
            />
            <span>Cryptographic details</span>
          </label>
          {verified && (
            <span className={`ic-badge ${verificationResult.status ? 'ic-badge-pass' : 'ic-badge-fail'}`}>
              {verificationResult.status
                ? <><Check size={12} strokeWidth={2.5} aria-hidden="true" />Verified</>
                : <><X size={12} strokeWidth={2.5} aria-hidden="true" />Failed</>}
            </span>
          )}
        </div>
      </div>

      {/* === Signature layer === */}
      {!minified && (
        <div className={`ic-node ic-node-${sigStatus}`}>
          <div className="ic-node-head">
            <StatusIcon status={sigStatus} />
            <span className="ic-node-title">Sigstore Signature</span>
          </div>
          {details && (
            <div className="ic-node-body">
              <div>Signed by <strong>{details.signerIdentity}</strong></div>
              <div>Uploaded by <strong>{record.uploaderIdentity ?? '—'}</strong></div>
              <div>
                <a
                  href={`https://search.sigstore.dev/?hash=${details.manifestHash}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  View Rekor transparency log.
                </a>
              </div>
            </div>
          )}
        </div>
      )}

      {showCrypto && (
        <>
          {/* Connector: signs */}
          <Connector label={sigConnectorLabel} status={sigStatus} />

          {/* === Manifest layer === */}
          <div className="ic-node">
            <div className="ic-node-head">
              <span className="ic-node-title">Manifest</span>
            </div>
            <div className="ic-manifest-hashes">
              <div className={`ic-hash ic-hash-${metaHashStatus}`}>
                <div className="ic-hash-head">
                  <StatusIcon status={metaHashStatus} />
                  <span>Metadata hash</span>
                </div>
                {manifest && (
                  <code className="ic-hash-value">
                    {manifest.metadataHashInfo.hashAlgorithm}: {manifest.metadataHashInfo.hashValue}
                  </code>
                )}
              </div>
              <div className={`ic-hash ic-hash-${filesHashStatus}`}>
                <div className="ic-hash-head">
                  <StatusIcon status={filesHashStatus} />
                  <span>File inventory hash</span>
                </div>
                {manifest && (
                  <code className="ic-hash-value">
                    {manifest.filesHashInfo.hashAlgorithm}: {manifest.filesHashInfo.hashValue}
                  </code>
                )}
              </div>
            </div>
          </div>

          {/* Split connectors: protects */}
          <div className="ic-split">
            <Connector label="protects" status={metaHashStatus} />
            <Connector label="protects" status={filesHashStatus} />
          </div>
        </>
      )}

      {/* === Data layer === */}
      <div className={showCrypto || minified ? 'ic-split' : 'ic-stack'}>
        {/* Metadata */}
        <div className={`ic-node ic-node-${metaHashStatus}`}>
          <div className="ic-node-head">
            <StatusIcon status={metaHashStatus} />
            <span className="ic-node-title">Metadata</span>
          </div>
          <div className="ic-node-body">
            <div className="ic-field">
              <span className="ic-field-label">Data ID</span>
              <strong className="ic-field-value">{metadata.dataId}</strong>
            </div>
            <div className="ic-field">
              <span className="ic-field-label">Data Type</span>
              <span className="ic-field-value">{metadata.dataType}</span>
            </div>
            {!minified && metadata.predecessors.length > 0 && (
              <div className="ic-field">
                <span className="ic-field-label">Predecessors</span>
                <span className="ic-field-value">
                  {`${metadata.predecessors.length} predecessor(s)`}
                </span>
              </div>
            )}
            {!minified && metadata.attributes && Object.entries(metadata.attributes)
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([key, value]) => (
                <div className="ic-field" key={`attr-${key}`}>
                  <span className="ic-field-label">{key}</span>
                  <span className="ic-field-value">{value}</span>
                </div>
              ))}
          </div>
        </div>

        {/* Files */}
        <div className={`ic-node ic-node-${filesNodeStatus}`}>
          <div className="ic-node-head">
            <StatusIcon status={filesNodeStatus} />
            <span className="ic-node-title">Files</span>
            {filesInfo && (
              <span className="ic-file-count">{filesInfo.files.length}</span>
            )}
          </div>
          <div className="ic-node-body">
            {fileVerificationResponse ? (
              <FilesWithVerification
                files={filesInfo?.files ?? []}
                fvr={fileVerificationResponse}
                uncheckedExpanded={uncheckedExpanded}
                setUncheckedExpanded={setUncheckedExpanded}
                predecessorFileResults={predecessorFileResults}
                isSearchingPredecessors={isSearchingPredecessors}
                showHash={showCrypto}
              />
            ) : filesInfo ? (
              <FilesCollapsed
                files={filesInfo.files}
                nodeStatus={filesNodeStatus}
                expanded={allFilesExpanded}
                setExpanded={setAllFilesExpanded}
                showHash={showCrypto}
              />
            ) : null}
            {fileVerificationResponse && (() => {
              const total = fileVerificationResponse.fileResults.length
              const matched =
                fileVerificationResponse.fileResults.filter(r => r.status === 'MATCHED').length +
                predecessorFileResults.filter(r => r.status === 'MATCHED').length
              return (
                <div className="ic-note">
                  {isSearchingPredecessors
                    ? `Checking ${total} ${total === 1 ? 'file' : 'files'}…`
                    : `${matched} of ${total} ${total === 1 ? 'file' : 'files'} matched`}
                </div>
              )
            })()}
            {!fileVerificationResponse && fileContentsStatus === 'skipped' && (
              <div className="ic-note">
                Content not stored — inventory verified by hash above
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Summary */}
      {!minified && (
        <div className="ic-summary">
          {!verified && (
            <span>Click <strong>Verify</strong> to check that nothing has been tampered with.</span>
          )}
          {verified && verificationResult.status && (
            <span>
              Changing any file, metadata field, or file listing would break
              the hash chain, invalidating the signature.
            </span>
          )}
          {verified && !verificationResult.status && (
            <span className="ic-summary-fail">
              <strong>Integrity check failed.</strong> {verificationResult.errorMessage}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
