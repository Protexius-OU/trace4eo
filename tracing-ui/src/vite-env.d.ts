/// <reference types="vite/client" />

interface Window {
  __ENV__?: {
    KEYCLOAK_URL?: string
  }
}
