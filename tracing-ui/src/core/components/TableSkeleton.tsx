type SkeletonWidth = 'wide' | 'medium' | 'narrow' | 'actions'

interface TableSkeletonProps {
  rows: number
  columns: SkeletonWidth[]
}

export function TableSkeleton({ rows, columns }: TableSkeletonProps) {
  return (
    <>
      {Array.from({ length: rows }).map((_, i) => (
        <tr key={i}>
          {columns.map((width, j) => (
            <td key={j}>
              <div className={`skeleton-cell skeleton-cell--${width}`} />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}
