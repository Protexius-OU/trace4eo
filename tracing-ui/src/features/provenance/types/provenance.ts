export interface Predecessor {
  id: string
}

export interface Metadata {
  dataId: string
  dataType: string
  predecessors: Predecessor[]
}

export interface ProvenanceSignature {
  signingTime: string
  hashAlgorithm: string
  details: SignatureDetails | null
}

export interface FileHashInfo {
  path: string | null
  hashAlgorithm: string
  hashValue: string
}

export interface Manifest {
  version: string
  metadataHashInfo: FileHashInfo
  filesHashInfo: FileHashInfo
}

export interface FilesInfoData {
  files: FileHashInfo[]
}

export interface ProvenanceRecord {
  id: string
  metadata: Metadata
  signature?: ProvenanceSignature
  manifest?: Manifest
  filesInfo?: FilesInfoData
  uploaderIdentity?: string | null
}

export interface GraphNode {
  id: string
  dataId: string
  dataType: string
  signingTime: string
  depth: number
  predecessorCount: number
  signerIdentity: string | null
}

export interface GraphEdge {
  sourceId: string
  targetId: string
}

export interface GroupNode {
  id: string            // "group::{parentNodeId}"
  parentNodeId: string
  hiddenNodeIds: string[]
  dataType: string
  depth: number
  count: number
  isGroup: true
}

export type DisplayNode = (GraphNode & { isGroup?: false }) | GroupNode

export interface GraphMetadata {
  totalNodes: number
  maxDepth: number
  missingPredecessors: string[]
}

export interface ProvenanceGraph {
  rootId: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  metadata: GraphMetadata
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface RecordFilters {
  dataTypes?: string[]
  dataId?: string
  signerIdentities?: string[]
}

export interface FilterOptions {
  dataTypes: string[]
  signerIdentities: string[]
}

export type VerificationStepName = 'SIGNATURE' | 'METADATA' | 'FILES_INFO' | 'FILE_CONTENTS'

export interface VerificationStep {
  name: VerificationStepName
  description: string
  status: boolean
  errorMessage: string | null
}

export interface VerificationResult {
  status: boolean
  error: string | null
  errorMessage: string | null
  steps: VerificationStep[]
}

export interface SignatureDetails {
  signingTime: string
  rekorLogIndex: string
  signerIdentity: string
  certificateIssuer: string
  manifestHash: string | null
}

export type FileCheckStatus = 'MATCHED' | 'MISMATCH' | 'NOT_IN_RECORD'

export interface FileCheckResult {
  filename: string
  recordPath: string | null
  status: FileCheckStatus
}

export interface FileVerificationResponse extends VerificationResult {
  fileResults: FileCheckResult[]
}

export interface PredecessorFileResult {
  filename: string
  foundInRecordId: string
  foundAtDepth: number
  status: 'MATCHED' | 'MISMATCH'
  recordDataId: string
  recordDataType: string
}

export type TraceVerificationStatus = 'OK' | 'TRACE_NOT_FOUND' | 'SIGNATURE_ERROR'

export interface Sentinel2VerificationResponse {
  status: TraceVerificationStatus
  imageId: string
  traceId: string | null
  hashAlgorithm: string | null
  signatureAlgorithm: string | null
}

export type Sentinel2FileVerificationStatus =
  | 'OK'
  | 'TRACE_NOT_FOUND'
  | 'SIGNATURE_ERROR'
  | 'HASH_MISMATCH'
  | 'FILE_NOT_IN_TRACE'

export interface Sentinel2FileVerificationResponse {
  status: Sentinel2FileVerificationStatus
  imageId: string
  filename: string
  providedHash: string
  expectedHash: string | null
  traceId: string | null
  hashAlgorithm: string | null
  signatureAlgorithm: string | null
}

export type Sentinel2DirectoryFileStatus = 'OK' | 'HASH_MISMATCH' | 'FILE_NOT_IN_TRACE'
export type Sentinel2DirectoryTraceStatus = 'OK' | 'TRACE_NOT_FOUND' | 'SIGNATURE_ERROR'

export interface Sentinel2DirectoryFileResult {
  filename: string
  status: Sentinel2DirectoryFileStatus
  providedHash: string
  expectedHash: string | null
}

export interface Sentinel2DirectoryVerificationResponse {
  traceStatus: Sentinel2DirectoryTraceStatus
  imageId: string
  traceId: string | null
  hashAlgorithm: string | null
  signatureAlgorithm: string | null
  totalFiles: number
  matchedFiles: number
  mismatchedFiles: number
  filesNotInTrace: number
  fileResults: Sentinel2DirectoryFileResult[]
}
