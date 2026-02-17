export function getSignerDomain(signerIdentity: string | null): string {
  if (!signerIdentity) return '-'
  const domain = signerIdentity.split('@')[1]?.split('.')[0]
  return domain ? domain.charAt(0).toUpperCase() + domain.slice(1) : '-'
}
