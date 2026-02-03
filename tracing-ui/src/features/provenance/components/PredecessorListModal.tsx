import { Link } from 'react-router-dom'
import type { GraphNode } from '../types/provenance'

interface Props {
  nodes: GraphNode[]
  onClose: () => void
}

export default function PredecessorListModal({ nodes, onClose }: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content-wide" onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ margin: 0 }}>Collapsed Predecessors ({nodes.length})</h2>
          <button
            className="btn btn-secondary"
            onClick={onClose}
            style={{ padding: '0.25rem 0.75rem' }}
          >
            X
          </button>
        </div>
        <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Data ID</th>
                <th>Type</th>
                <th>Signed</th>
                <th>Signed by</th>
              </tr>
            </thead>
            <tbody>
              {nodes.map(node => (
                <tr key={node.id}>
                  <td>
                    <Link
                      to={`/records/${node.id}/graph`}
                      style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}
                      onClick={onClose}
                    >
                      {node.dataId || node.id}
                    </Link>
                  </td>
                  <td>
                    <span className="badge badge-type">{node.dataType || 'Unknown'}</span>
                  </td>
                  <td>
                    {node.signingTime
                      ? new Date(node.signingTime).toLocaleDateString()
                      : '-'}
                  </td>
                  <td style={{ fontSize: '0.8rem' }}>
                    {node.signerIdentity || '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
