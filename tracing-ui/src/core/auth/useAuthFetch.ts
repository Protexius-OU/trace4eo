import { useCallback } from 'react'
import { useAuth } from 'react-oidc-context'
import { authFetch, type FetchFn } from './authFetch'

export function useAuthFetch(): FetchFn {
  const { user } = useAuth()
  const token = user?.access_token ?? null
  return useCallback(
    (input: RequestInfo | URL, init?: RequestInit) => authFetch(input, init, token),
    [token],
  )
}
