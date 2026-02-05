import type { User } from 'oidc-client-ts'

let currentUser: User | null = null

export function setCurrentUser(user: User | null) {
  currentUser = user
}

export async function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers)
  if (currentUser?.access_token) {
    headers.set('Authorization', `Bearer ${currentUser.access_token}`)
  }
  return fetch(input, { ...init, headers })
}
