interface TypeBadgeProps {
  type: string | null | undefined
}

export function TypeBadge({ type }: TypeBadgeProps) {
  return <span className="badge badge-type">{type || 'Unknown'}</span>
}
