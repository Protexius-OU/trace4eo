import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { ProvenanceRecord } from '../types/provenance'
import { downloadZip } from '../api/provenanceApi'

interface Props {
  records: ProvenanceRecord[]
}

export default function RecordTable({ records }: Props) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const handleDownload = async (id: string) => {
    setDownloadingId(id)
    try {
      await downloadZip(id)
    } catch (err) {
      console.error('Download failed:', err)
    } finally {
      setDownloadingId(null)
    }
  }

  if (records.length === 0) {
    return <p className="loading">No records found</p>
  }

  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Data ID</th>
            <th>Type</th>
            <th>Direct Predecessors</th>
            <th style={{ width: '1%', whiteSpace: 'nowrap' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={record.id}>
              <td className="uuid">{record.id}</td>
              <td>{record.metadata.dataId}</td>
              <td>
                <span className="badge badge-type">{record.metadata.dataType}</span>
              </td>
              <td>{record.metadata.predecessors?.length ?? 0}</td>
              <td>
                <div style={{ display: 'flex', gap: '0.5rem', whiteSpace: 'nowrap' }}>
                  <Link to={`/records/${record.id}/graph`} className="btn btn-primary">
                    View Details
                  </Link>
                  <button
                    onClick={() => handleDownload(record.id)}
                    disabled={downloadingId === record.id}
                    className="btn btn-secondary"
                  >
                    {downloadingId === record.id ? '...' : 'Download'}
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
