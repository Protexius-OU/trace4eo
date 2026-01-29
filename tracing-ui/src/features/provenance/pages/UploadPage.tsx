import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { uploadFile, fetchRecords } from '../api/provenanceApi'
import type { ProvenanceRecord } from '../types/provenance'

export default function UploadPage() {
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [dataType, setDataType] = useState('')
  const [dataId, setDataId] = useState('')
  const [selectedPredecessors, setSelectedPredecessors] = useState<string[]>([])
  const [availableRecords, setAvailableRecords] = useState<ProvenanceRecord[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // TODO: Remove this dropdown once proper predecessor selection UI is implemented
  useEffect(() => {
    fetchRecords(0, 100).then(response => {
      setAvailableRecords(response.content)
    }).catch(console.error)
  }, [])

  const handlePredecessorChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const selected = Array.from(e.target.selectedOptions, option => option.value)
    setSelectedPredecessors(selected)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file || !dataType || !dataId) return

    setIsUploading(true)
    setError(null)

    try {
      const record = await uploadFile(file, dataType, dataId, selectedPredecessors.length > 0 ? selectedPredecessors : undefined)
      navigate(`/records/${record.id}/graph`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1>Upload File</h1>
        <Link to="/" className="btn btn-secondary">Back to List</Link>
      </div>

      <div className="upload-card" style={{ margin: '0 auto' }}>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="file">File</label>
            <input
              id="file"
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              required
            />
            {file && (
              <p className="file-info">{file.name} ({(file.size / 1024).toFixed(1)} KB)</p>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="dataType">Record Type</label>
            <input
              id="dataType"
              type="text"
              placeholder="e.g. satellite-image, ai-model"
              value={dataType}
              onChange={(e) => setDataType(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="dataId">Data ID</label>
            <input
              id="dataId"
              type="text"
              placeholder="e.g. acquisition-2024/scene-001"
              value={dataId}
              onChange={(e) => setDataId(e.target.value)}
              required
            />
          </div>

          {/* TODO: Remove this dropdown once proper predecessor selection UI is implemented */}
          {availableRecords.length > 0 && (
            <div className="form-group">
              <label htmlFor="predecessors">Predecessors (optional)</label>
              <select
                id="predecessors"
                multiple
                value={selectedPredecessors}
                onChange={handlePredecessorChange}
                style={{ minHeight: '100px' }}
              >
                {availableRecords.map(record => (
                  <option key={record.id} value={record.id}>
                    {record.metadata.dataId} ({record.metadata.dataType})
                  </option>
                ))}
              </select>
              <p className="file-info">Hold Ctrl/Cmd to select multiple</p>
            </div>
          )}

          {error && <p className="error">{error}</p>}

          <button
            type="submit"
            className="btn btn-primary"
            disabled={isUploading || !file || !dataType || !dataId}
            style={{ marginTop: '1rem' }}
          >
            {isUploading ? 'Uploading...' : 'Upload & Sign'}
          </button>
        </form>
      </div>
    </div>
  )
}
