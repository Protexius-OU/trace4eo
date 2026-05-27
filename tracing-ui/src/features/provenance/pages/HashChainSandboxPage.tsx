import IntegrityChainSandbox from '../components/IntegrityChainSandbox'
import '../components/IntegrityChainSandbox.css'

export default function HashChainSandboxPage() {
  return (
    <div>
      <p className="scb-intro">
        A provenance record seals its data in nested SHA-256 hashes: each file is hashed, the file
        inventory is hashed, the manifest binds those hashes, and the manifest is signed. Edit the
        sample source data below and click <strong>Verify</strong> to watch a single change ripple up
        the chain and break the signature — exactly what the verifier detects.
      </p>
      <IntegrityChainSandbox />
    </div>
  )
}
