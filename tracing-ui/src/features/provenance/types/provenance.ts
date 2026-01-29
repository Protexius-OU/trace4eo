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

export interface ProvenanceRecord {
  id: string
  metadata: Metadata
  signature?: ProvenanceSignature
  // filesInfo, manifest omitted for list view
}

export interface GraphNode {
  id: string
  dataId: string
  dataType: string
  signingTime: string
  depth: number
  predecessorCount: number
}

export interface GraphEdge {
  sourceId: string
  targetId: string
}

export interface GraphMetadata {
  totalNodes: number
  maxDepth: number
  requestedDepthLimit: number
  depthLimitReached: boolean
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
  dataType?: string
  dataId?: string
  fromDate?: string
  toDate?: string
}

export interface VerificationStep {
  name: string
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
  oidcIssuer: string
  certificateIssuer: string
}
