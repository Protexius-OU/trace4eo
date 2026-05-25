export type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export function authFetch(
  input: RequestInfo | URL,
  init: RequestInit | undefined,
  token: string | null | undefined,
): Promise<Response> {
  const headers = new Headers(init?.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(input, { ...init, headers })
}
