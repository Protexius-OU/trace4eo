import React from 'react'
import type { ProvenanceRecord, VerificationResult, VerificationStepName, FileVerificationResponse } from '../types/provenance'
import './IntegrityChain.css'

interface Props {
  record: ProvenanceRecord
  verificationResult: VerificationResult | null
  fileVerificationResponse?: FileVerificationResponse | null
}

type Status = 'idle' | 'pass' | 'fail' | 'skipped'

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
  switch (status) {
    case 'pass': return <span className="ic-status ic-status-pass">✓</span>
    case 'fail': return <span className="ic-status ic-status-fail">✗</span>
    case 'skipped': return <span className="ic-status ic-status-skipped">&ndash;</span>
    default: return null
  }
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

export default function IntegrityChain({ record, verificationResult, fileVerificationResponse }: Props) {
  const { manifest, filesInfo, metadata, signature } = record
  const details = signature?.details

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
    <div className="integrity-chain">
      <div className="ic-header">
        <span className="ic-title">Integrity Chain</span>
        {verified && (
          <span className={`ic-badge ${verificationResult.status ? 'ic-badge-pass' : 'ic-badge-fail'}`}>
            {verificationResult.status ? '✓ Verified' : '✗ Failed'}
          </span>
        )}
      </div>

      {/* === Signature layer === */}
      <div className={`ic-node ic-node-${sigStatus}`}>
        <div className="ic-node-head">
          <StatusIcon status={sigStatus} />
          <span className="ic-node-title">Sigstore Signature</span>
        </div>
        {details && (
          <div className="ic-node-body">
            <div>Signed by <strong>{details.signerIdentity}</strong></div>
            <div>
              <a
                href={`https://search.sigstore.dev/?logIndex=${details.rekorLogIndex}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                Transparency log #{details.rekorLogIndex}
              </a>
            </div>
          </div>
        )}
      </div>

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

      {/* === Data layer === */}
      <div className="ic-split">
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
            <div className="ic-field">
              <span className="ic-field-label">Predecessors</span>
              <span className="ic-field-value">
                {metadata.predecessors.length === 0
                  ? 'None.'
                  : `${metadata.predecessors.length} predecessor(s)`}
              </span>
            </div>
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
            {filesInfo?.files.map(file => {
              const fileResult = fileVerificationResponse?.fileResults.find(r => r.recordPath === file.path)
              const isDimmed = fileVerificationResponse != null && fileResult == null
              const perFileStatus: Status = fileResult
                ? (fileResult.status === 'MATCHED' ? 'pass' : 'fail')
                : filesNodeStatus
              return (
                <div key={file.path} className={`ic-file${isDimmed ? ' ic-file-dimmed' : ''}`}>
                  <StatusIcon status={perFileStatus} />
                  <div className="ic-file-info">
                    <span className="ic-file-path">{file.path}</span>
                    <code className="ic-hash-value">{file.hashAlgorithm}: {file.hashValue}</code>
                  </div>
                </div>
              )
            })}
            {fileVerificationResponse && (
              <div className="ic-note">
                Checked {fileVerificationResponse.fileResults.length} of {filesInfo?.files.length ?? 0} files
              </div>
            )}
            {!fileVerificationResponse && fileContentsStatus === 'skipped' && (
              <div className="ic-note">
                Content not stored — inventory verified by hash above
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Summary */}
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
    </div>
  )
}
