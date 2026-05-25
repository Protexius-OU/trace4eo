import { useReducer, useEffect, useRef } from 'react'
import type React from 'react'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { verifySentinel2Trace, verifySentinel2Files } from '../api/provenanceApi'
import { hashFileBlake3Hex } from '../utils/hashFiles'
import type {
  Sentinel2VerificationResponse,
  Sentinel2HashCheckResponse,
} from '../types/provenance'

type State = {
  isVerifyingTrace: boolean
  traceVerificationResult: Sentinel2VerificationResponse | null
  traceVerificationError: string | null
  traceCheckStatus: string | null
  traceCheckResult: Sentinel2HashCheckResponse | null
  traceCheckError: string | null
}

type Action =
  | { type: 'RESET' }
  | { type: 'TRACE_START' }
  | { type: 'TRACE_DONE'; result: Sentinel2VerificationResponse }
  | { type: 'TRACE_ERROR'; error: string }
  | { type: 'FILE_CHECK_START' }
  | { type: 'FILE_CHECK_STATUS'; status: string }
  | { type: 'FILE_CHECK_DONE'; result: Sentinel2HashCheckResponse }
  | { type: 'FILE_CHECK_ERROR'; error: string }
  | { type: 'FILE_CHECK_END' }

const initial: State = {
  isVerifyingTrace: false,
  traceVerificationResult: null,
  traceVerificationError: null,
  traceCheckStatus: null,
  traceCheckResult: null,
  traceCheckError: null,
}

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'RESET':
      return initial
    case 'TRACE_START':
      return { ...state, isVerifyingTrace: true, traceVerificationResult: null, traceVerificationError: null }
    case 'TRACE_DONE':
      return { ...state, isVerifyingTrace: false, traceVerificationResult: action.result }
    case 'TRACE_ERROR':
      return { ...state, isVerifyingTrace: false, traceVerificationError: action.error }
    case 'FILE_CHECK_START':
      return { ...state, traceCheckResult: null, traceCheckError: null }
    case 'FILE_CHECK_STATUS':
      return { ...state, traceCheckStatus: action.status }
    case 'FILE_CHECK_DONE':
      return { ...state, traceCheckResult: action.result }
    case 'FILE_CHECK_ERROR':
      return { ...state, traceCheckError: action.error }
    case 'FILE_CHECK_END':
      return { ...state, traceCheckStatus: null }
  }
}

export function useSentinel2Verification(id: string | undefined) {
  const authFetch = useAuthFetch()
  const [state, dispatch] = useReducer(reducer, initial)
  const traceFileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    dispatch({ type: 'RESET' })
  }, [id])

  const handleVerifyTrace = async () => {
    if (!id) return
    dispatch({ type: 'TRACE_START' })
    try {
      const result = await verifySentinel2Trace(authFetch, id)
      dispatch({ type: 'TRACE_DONE', result })
    } catch (err) {
      dispatch({ type: 'TRACE_ERROR', error: err instanceof Error ? err.message : 'Trace verification failed' })
    }
  }

  const handleVerifyTraceFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (!files.length || !id) return

    dispatch({ type: 'FILE_CHECK_START' })
    try {
      const entries: Array<{ filename: string; hashHex: string }> = []
      for (let i = 0; i < files.length; i++) {
        const f = files[i]!
        const sizeMb = (f.size / 1_048_576).toFixed(1)
        const prefix = files.length === 1 ? '' : `File ${i + 1} of ${files.length}: `
        dispatch({ type: 'FILE_CHECK_STATUS', status: `${prefix}Hashing ${f.name} (${sizeMb} MB)…` })
        const hashHex = await hashFileBlake3Hex(f)
        entries.push({ filename: f.name, hashHex })
      }
      dispatch({
        type: 'FILE_CHECK_STATUS',
        status: `Sending ${entries.length === 1 ? 'hash' : `${entries.length} hashes`} to server for comparison…`,
      })
      const result = await verifySentinel2Files(authFetch, id, entries)
      dispatch({ type: 'FILE_CHECK_DONE', result })
    } catch (err) {
      dispatch({ type: 'FILE_CHECK_ERROR', error: err instanceof Error ? err.message : 'Verification failed' })
    } finally {
      dispatch({ type: 'FILE_CHECK_END' })
    }
  }

  return { state, traceFileInputRef, handleVerifyTrace, handleVerifyTraceFile }
}
