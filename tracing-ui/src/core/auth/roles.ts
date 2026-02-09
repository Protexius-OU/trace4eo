import type { User } from 'oidc-client-ts'

export function getUserRoles(user: User | null | undefined): string[] {
  if (!user?.access_token) return []
  try {
    const payload = JSON.parse(atob(user.access_token.split('.')[1]))
    return payload?.realm_access?.roles ?? []
  } catch {
    return []
  }
}

export function hasRole(user: User | null | undefined, role: string): boolean {
  return getUserRoles(user).includes(role)
}
