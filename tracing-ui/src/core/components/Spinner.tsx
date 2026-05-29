import './Spinner.css'

interface Props {
  label?: string
  size?: 'sm' | 'md'
  fill?: boolean
}

export default function Spinner({ label, size = 'md', fill }: Props) {
  return (
    <div
      className={`spinner-page${fill ? ' spinner-page--fill' : ''}`}
      role="status"
      aria-label={label ?? 'Loading'}
    >
      <div className={`spinner-ring spinner-ring--${size}`} />
      {label && <span className="spinner-label" aria-hidden="true">{label}</span>}
    </div>
  )
}
