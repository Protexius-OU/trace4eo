import type { User } from 'oidc-client-ts'

let getUser: () => User | null = () => null

export function setUserGetter(getter: () => User | null) {
  getUser = getter
}

export async function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers)
  const user = getUser()
  if (user?.access_token) {
    headers.set('Authorization', `Bearer ${user.access_token}`)
  }
  return fetch(input, { ...init, headers })
}
