import './IntegrityChain.css'
import './IntegrityChainSandbox.css'
import { useHashChainSandbox, type SandboxLayer, type LayerStatus } from '../hooks/useHashChainSandbox'

function StatusIcon({ status }: { status: LayerStatus }) {
  switch (status) {
    case 'pass': return <span className="ic-status ic-status-pass">✓</span>
    case 'fail': return <span className="ic-status ic-status-fail">✗</span>
    default: return null
  }
}

function nodeClass(status: LayerStatus): string {
  if (status === 'fail') return ' ic-node-fail'
  if (status === 'pass') return ' ic-node-pass'
  return ''
}

function hashBoxClass(status: LayerStatus): string {
  if (status === 'fail') return ' ic-hash-fail'
  if (status === 'pass') return ' ic-hash-pass'
  return ''
}

function connectorClass(status: LayerStatus): string {
  if (status === 'fail') return 'fail'
  if (status === 'pass') return 'pass'
  return 'idle'
}

function Connector({ label, status }: { label: string; status: LayerStatus }) {
  return (
    <div className={`ic-connector ic-connector-${connectorClass(status)}`}>
      <div className="ic-line" />
      <span className="ic-label">{label}</span>
      <div className="ic-line" />
    </div>
  )
}

/** Shows the freshly recomputed hash above the value sealed into the record.
 *  `full` displays the whole hash (used in the wide signature node); otherwise
 *  it is truncated with an ellipsis to fit the narrow chain columns. */
function HashCompare({ layer, full = false }: { layer: SandboxLayer; full?: boolean }) {
  const liveChanged = layer.status === 'fail'
  const truncate = full ? '' : ' scb-hash-value'
  return (
    <div className="scb-hashes">
      <div className="scb-hash-row">
        <span className="scb-hash-tag">expected</span>
        <code className={`ic-hash-value${truncate} scb-hash-sealed`} title={layer.sealed}>
          {layer.sealed}
        </code>
      </div>
      <div className="scb-hash-row">
        <span className="scb-hash-tag">actual</span>
        <code
          className={`ic-hash-value${truncate}${liveChanged ? ' scb-hash-changed' : ''}`}
          title={layer.live}
        >
          {layer.live}
        </code>
      </div>
    </div>
  )
}

export default function IntegrityChainSandbox() {
  const sb = useHashChainSandbox()

  return (
    <div className="integrity-chain ic-minified scb">
      <div className="ic-header">
        <span className="ic-title">Hash-Chain Sandbox</span>
        <div className="ic-header-actions">
          <button className="btn btn-secondary" onClick={sb.reset}>Reset</button>
          <button className="btn btn-primary" onClick={sb.verify}>Verify</button>
          {sb.verified && (
            <span className={`ic-badge ${sb.overallPass ? 'ic-badge-pass' : 'ic-badge-fail'}`}>
              {sb.overallPass ? '✓ Verified' : '✗ Failed'}
            </span>
          )}
        </div>
      </div>

      {/* === Signature === */}
      <div className={`ic-node${nodeClass(sb.signature.status)}`}>
        <div className="ic-node-head">
          <StatusIcon status={sb.signature.status} />
          <span className="ic-node-title">Sigstore Signature</span>
        </div>
        <div className="ic-node-body">
          <div>Recomputed manifest hash vs. the value the signature attests to:</div>
          <HashCompare layer={sb.signature} full />
        </div>
      </div>

      <Connector label="signs manifest hash" status={sb.signature.status} />

      {/* === Manifest === */}
      <div className="ic-node">
        <div className="ic-node-head">
          <span className="ic-node-title">Manifest</span>
        </div>
        <div className="ic-manifest-hashes">
          <div className={`ic-hash${hashBoxClass(sb.metadata.status)}`}>
            <div className="ic-hash-head">
              <StatusIcon status={sb.metadata.status} />
              <span>Metadata hash</span>
            </div>
            <HashCompare layer={sb.metadata} />
          </div>
          <div className={`ic-hash${hashBoxClass(sb.filesInfo.status)}`}>
            <div className="ic-hash-head">
              <StatusIcon status={sb.filesInfo.status} />
              <span>File inventory hash</span>
            </div>
            <HashCompare layer={sb.filesInfo} />
          </div>
        </div>
      </div>

      <div className="ic-split">
        <Connector label="protects metadata" status={sb.metadata.status} />
        <Connector label="protects file inventory" status={sb.filesInfo.status} />
      </div>

      {/* === Data layer === */}
      <div className="ic-split">
        {/* Metadata (static) */}
        <div className={`ic-node${nodeClass(sb.metadata.status)}`}>
          <div className="ic-node-head">
            <StatusIcon status={sb.metadata.status} />
            <span className="ic-node-title">Metadata</span>
          </div>
          <div className="ic-node-body">
            <div className="ic-field">
              <span className="ic-field-label">Data ID</span>
              <strong className="ic-field-value">{sb.metadataView.dataId}</strong>
            </div>
            <div className="ic-field">
              <span className="ic-field-label">Data Type</span>
              <span className="ic-field-value">{sb.metadataView.dataType}</span>
            </div>
            <div className="ic-field">
              <span className="ic-field-label">Location</span>
              <input
                className="scb-field-input"
                value={sb.location}
                onChange={e => sb.setLocation(e.target.value)}
                spellCheck={false}
                aria-label="Location (custom metadata attribute)"
              />
            </div>
            <div className="ic-note">
              Hashed independently of the file bytes — editing Location breaks the metadata branch
              (and the signature), but not the file-inventory branch.
            </div>
          </div>
        </div>

        {/* Files (editable source data) */}
        <div className={`ic-node${nodeClass(sb.fileContents.status)}`}>
          <div className="ic-node-head">
            <StatusIcon status={sb.fileContents.status} />
            <span className="ic-node-title">Files</span>
          </div>
          <div className="ic-node-body">
            <div className="ic-field">
              <span className="ic-field-label">Path</span>
              <code className="ic-field-value scb-mono">{sb.filePath}</code>
            </div>
            <label className="scb-input-label" htmlFor="scb-input">
              Source data — edit to tamper (in production this is the EO product, e.g. a GeoTIFF)
            </label>
            <textarea
              id="scb-input"
              className="scb-input"
              value={sb.inputData}
              onChange={e => sb.setInputData(e.target.value)}
              spellCheck={false}
              rows={6}
            />
            <HashCompare layer={sb.fileContents} />
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="ic-summary">
        Editing the source data re-hashes the file and the file inventory; editing Location re-hashes
        the metadata. Either way the manifest hash changes and the signature no longer matches. Click{' '}
        <strong>Verify</strong> to compare against the signed baseline — the affected branch and the
        signature turn <span className="ic-status-fail">✗</span> red, while the untouched branch stays{' '}
        <span className="ic-status-pass">✓</span>. Because the record's UUID is derived from this
        signature, re-signing to hide a change would also change the UUID and orphan any record that
        lists this one as a predecessor. <strong>Reset</strong> restores the originals.
      </div>
    </div>
  )
}
