import { useMemo, useState } from 'react'
import { sha256 } from '@noble/hashes/sha2.js'

function sha256Hex(input: string): string {
  const digest = sha256.create().update(new TextEncoder().encode(input)).digest()
  return Array.from(digest, b => b.toString(16).padStart(2, '0')).join('')
}

export type LayerStatus = 'idle' | 'pass' | 'fail'

export interface SandboxLayer {
  /** Hash recomputed from the current (possibly edited) data. */
  live: string
  /** Hash sealed into the record when it was signed (the frozen baseline). */
  sealed: string
  /** Whether the recomputed hash diverges from the sealed one. */
  changed: boolean
  /** idle before Verify; pass/fail once verified. */
  status: LayerStatus
}

// ── Built-in sample record ────────────────────────────────────────────────
// Two editable fields stand in for real provenance data: the file's source
// bytes and a custom `location` metadata attribute. Everything else is fixed.

const FILE_PATH = 'B04_10m.tif'

const SAMPLE_INPUT = `Sentinel-2 L2A — band B04 (red, 10 m)
tile: T34UFV   acquired: 2024-08-12T10:21:33Z
crs: EPSG:32634   size: 10980 x 10980

<binary GeoTIFF pixel data — shown as editable text for the demo>`

const SAMPLE_LOCATION = '59.4370°N, 24.7536°E'

const DATA_ID = 'S2A_MSIL2A_20240812T102133_N0510_R065_T34UFV'
const DATA_TYPE = 'Sentinel-2 L2A'

function metadataOf(location: string) {
  return {
    dataId: DATA_ID,
    dataType: DATA_TYPE,
    predecessors: [{ id: '018f3a2b-7c44-8e10-9b21-5d7e9c0a1f33' }],
    attributes: { location, processor: 'sen2cor 2.11', processingLevel: 'L2A' },
  }
}

function filesInfoOf(contentHash: string) {
  return { files: [{ path: FILE_PATH, hashAlgorithm: 'SHA256', hashValue: contentHash }] }
}

function manifestOf(metadataHash: string, filesInfoHash: string) {
  return {
    version: '1',
    metadataHashInfo: { hashAlgorithm: 'SHA256', hashValue: metadataHash },
    filesHashInfo: { hashAlgorithm: 'SHA256', hashValue: filesInfoHash },
  }
}

function chainHashes(input: string, location: string) {
  const contentHash = sha256Hex(input)
  const filesInfoHash = sha256Hex(JSON.stringify(filesInfoOf(contentHash)))
  const metadataHash = sha256Hex(JSON.stringify(metadataOf(location)))
  const manifestHash = sha256Hex(JSON.stringify(manifestOf(metadataHash, filesInfoHash)))
  return { contentHash, filesInfoHash, metadataHash, manifestHash }
}

export interface HashChainSandbox {
  inputData: string
  setInputData: (value: string) => void
  location: string
  setLocation: (value: string) => void
  verified: boolean
  verify: () => void
  reset: () => void
  fileContents: SandboxLayer
  filesInfo: SandboxLayer
  metadata: SandboxLayer
  signature: SandboxLayer
  overallPass: boolean
  filePath: string
  metadataView: { dataId: string; dataType: string }
}

export function useHashChainSandbox(): HashChainSandbox {
  const [inputData, setInputData] = useState(SAMPLE_INPUT)
  const [location, setLocation] = useState(SAMPLE_LOCATION)
  const [verified, setVerified] = useState(false)

  // Frozen at load — represents what was signed.
  const baseline = useMemo(() => chainHashes(SAMPLE_INPUT, SAMPLE_LOCATION), [])
  // Recomputed from the current inputs; each level feeds the next.
  const live = useMemo(() => chainHashes(inputData, location), [inputData, location])

  function layer(liveHash: string, sealedHash: string): SandboxLayer {
    const changed = liveHash !== sealedHash
    const status: LayerStatus = !verified ? 'idle' : changed ? 'fail' : 'pass'
    return { live: liveHash, sealed: sealedHash, changed, status }
  }

  const fileContents = layer(live.contentHash, baseline.contentHash)
  const filesInfo = layer(live.filesInfoHash, baseline.filesInfoHash)
  const metadata = layer(live.metadataHash, baseline.metadataHash)
  const signature = layer(live.manifestHash, baseline.manifestHash)

  const overallPass =
    !fileContents.changed && !filesInfo.changed && !metadata.changed && !signature.changed

  return {
    inputData,
    setInputData,
    location,
    setLocation,
    verified,
    verify: () => setVerified(true),
    reset: () => {
      setInputData(SAMPLE_INPUT)
      setLocation(SAMPLE_LOCATION)
      setVerified(false)
    },
    fileContents,
    filesInfo,
    metadata,
    signature,
    overallPass,
    filePath: FILE_PATH,
    metadataView: { dataId: DATA_ID, dataType: DATA_TYPE },
  }
}
