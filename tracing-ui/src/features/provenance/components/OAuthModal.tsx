import './Modal.css'

interface Props {
  url: string
  onClose?: () => void
}

export default function OAuthModal({ url, onClose }: Props) {
  const handleOpenAuth = () => {
    window.open(url, '_blank')
  }

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-spinner" />
        <h2>Authentication Required</h2>
        <p>Please authenticate with Sigstore to sign your provenance record.</p>
        <div className="modal-actions">
          <button className="btn btn-primary" onClick={handleOpenAuth}>
            Open Authentication Page
          </button>
          {onClose && (
            <button className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
          )}
        </div>
        <p className="modal-hint">
          A new browser tab will open. Complete the authentication and return here.
        </p>
      </div>
    </div>
  )
}
