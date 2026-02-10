import type { User } from 'oidc-client-ts'

export function getUserRoles(user: User | null | undefined): string[] {
  if (!user?.profile) return []
  const realmAccess = user.profile.realm_access as { roles?: string[] } | undefined
  return realmAccess?.roles ?? []
}

export function hasRole(user: User | null | undefined, role: string): boolean {
  return getUserRoles(user).includes(role)
}
