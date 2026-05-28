import { useReducer, useEffect, useRef } from 'react'
import type React from 'react'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { verifyRecord, verifyFileHashes } from '../api/provenanceApi'
import { hashFile } from '../utils/hashFiles'
import type {
  VerificationResult,
  FileVerificationResponse,
  PredecessorFileResult,
  ProvenanceGraph,
  ProvenanceRecord,
} from '../types/provenance'

type State = {
  isVerifying: boolean
  verificationResult: VerificationResult | null
  isVerifyingFiles: boolean
  fileVerificationResponse: FileVerificationResponse | null
  predecessorFileResults: PredecessorFileResult[]
  isSearchingPredecessors: boolean
}

type Action =
  | { type: 'RESET' }
  | { type: 'VERIFY_START' }
  | { type: 'VERIFY_DONE'; result: VerificationResult }
  | { type: 'FILES_START' }
  | { type: 'FILES_RESPONSE'; response: FileVerificationResponse }
  | { type: 'FILES_END' }
  | { type: 'PREDECESSORS_START' }
  | { type: 'PREDECESSORS_DONE'; results: PredecessorFileResult[] }

const initial: State = {
  isVerifying: false,
  verificationResult: null,
  isVerifyingFiles: false,
  fileVerificationResponse: null,
  predecessorFileResults: [],
  isSearchingPredecessors: false,
}

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'RESET':
      return initial
    case 'VERIFY_START':
      return { ...state, isVerifying: true, verificationResult: null }
    case 'VERIFY_DONE':
      return { ...state, isVerifying: false, verificationResult: action.result }
    case 'FILES_START':
      return { ...state, isVerifyingFiles: true, fileVerificationResponse: null, predecessorFileResults: [], isSearchingPredecessors: false }
    case 'FILES_RESPONSE':
      return { ...state, fileVerificationResponse: action.response }
    case 'FILES_END':
      return { ...state, isVerifyingFiles: false }
    case 'PREDECESSORS_START':
      return { ...state, isSearchingPredecessors: true }
    case 'PREDECESSORS_DONE':
      return { ...state, isSearchingPredecessors: false, predecessorFileResults: action.results }
  }
}

export function useRecordVerification(
  id: string | undefined,
  graphData: ProvenanceGraph | undefined,
  recordData: ProvenanceRecord | undefined,
) {
  const authFetch = useAuthFetch()
  const [state, dispatch] = useReducer(reducer, initial)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    dispatch({ type: 'RESET' })
  }, [id])

  const handleVerify = async () => {
    if (!id) return
    dispatch({ type: 'VERIFY_START' })
    try {
      const result = await verifyRecord(authFetch, id)
      dispatch({ type: 'VERIFY_DONE', result })
    } catch (err) {
      dispatch({
        type: 'VERIFY_DONE',
        result: {
          status: false,
          error: 'UNKNOWN_ERROR',
          errorMessage: err instanceof Error ? err.message : 'Verification failed',
          steps: [],
        },
      })
    }
  }

  const handleVerifyFiles = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    if (files.length === 0 || !id) return

    dispatch({ type: 'FILES_START' })
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
      dispatch({ type: 'FILES_RESPONSE', response: result })

      const notFound = result.fileResults.filter(r => r.status === 'NOT_IN_RECORD')
      if (notFound.length > 0 && graphData) {
        dispatch({ type: 'PREDECESSORS_START' })
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
            console.debug('Skipping predecessor during file search:', err)
          }
        }
        dispatch({ type: 'PREDECESSORS_DONE', results: found })
      }
    } catch (err) {
      dispatch({
        type: 'FILES_RESPONSE',
        response: {
          status: false,
          error: 'UNKNOWN_ERROR',
          errorMessage: err instanceof Error ? err.message : 'File verification failed',
          steps: [],
          fileResults: [],
        },
      })
    } finally {
      dispatch({ type: 'FILES_END' })
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return { state, fileInputRef, handleVerify, handleVerifyFiles }
}
