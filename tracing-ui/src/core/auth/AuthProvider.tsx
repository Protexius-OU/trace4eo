import { AuthProvider as OidcAuthProvider } from 'react-oidc-context'
import { WebStorageStateStore } from 'oidc-client-ts'
import type { ReactNode } from 'react'

const oidcConfig = {
  authority: window.__ENV__?.KEYCLOAK_URL || import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180/realms/trace4eo',
  client_id: 'trace4eo-ui',
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  scope: 'openid email profile',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  onSigninCallback: (): void => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}

export function AuthProvider({ children }: { children: ReactNode }) {
  return (
    <OidcAuthProvider {...oidcConfig}>
      {children}
    </OidcAuthProvider>
  )
}
