import { X } from 'lucide-react'
import { useEscapeKey } from '../hooks/useEscapeKey'
import './Modal.css'

interface ModalProps {
  title: React.ReactNode
  onClose: () => void
  wide?: boolean
  children: React.ReactNode
}

export function Modal({ title, onClose, wide = true, children }: ModalProps) {
  useEscapeKey(onClose)

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className={`modal-content${wide ? ' modal-content-wide' : ''}`}
        onClick={e => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          <button
            type="button"
            className="btn btn-icon"
            onClick={onClose}
            aria-label="Close"
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
