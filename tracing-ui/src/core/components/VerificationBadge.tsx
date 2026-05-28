import { Check, X } from 'lucide-react'

interface VerificationBadgeProps {
  passed: boolean
}

export function VerificationBadge({ passed }: VerificationBadgeProps) {
  return (
    <span className={`ic-badge ${passed ? 'ic-badge-pass' : 'ic-badge-fail'}`}>
      {passed
        ? <><Check size={12} strokeWidth={2.5} aria-hidden="true" />Verified</>
        : <><X size={12} strokeWidth={2.5} aria-hidden="true" />Failed</>}
    </span>
  )
}
