import { useState, useMemo, useRef, useEffect } from 'react'
import './RecordTable.css'
import { Link } from 'react-router-dom'
import type { ProvenanceRecord } from '../types/provenance'
import { downloadZip } from '../api/provenanceApi'
import { getSignerDomain } from '../utils/signerIdentity'

interface Props {
  records: ProvenanceRecord[]
}

interface TooltipProps {
  text: string | null
  children: React.ReactNode
}

function Tooltip({ text, children }: TooltipProps) {
  const [show, setShow] = useState(false)
  const [position, setPosition] = useState({ x: 0, y: 0 })
  const ref = useRef<HTMLSpanElement>(null)

  const handleMouseEnter = () => {
    if (text && ref.current) {
      const rect = ref.current.getBoundingClientRect()
      setPosition({ x: rect.left, y: rect.bottom + 4 })
      setShow(true)
    }
  }

  if (!text) {
    return <span>{children}</span>
  }

  return (
    <>
      <span
        ref={ref}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={() => setShow(false)}
        style={{ cursor: 'help' }}
      >
        {children}
      </span>
      {show && (
        <div
          className="tooltip"
          style={{ left: position.x, top: position.y }}
        >
          {text}
        </div>
      )}
    </>
  )
}

interface FilterDropdownProps {
  label: string
  values: string[]
  selected: Set<string>
  onToggle: (value: string) => void
  onSelectAll: () => void
  onClearAll: () => void
}

function FilterDropdown({ label, values, selected, onToggle, onSelectAll, onClearAll }: FilterDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const allSelected = selected.size === values.length
  const noneSelected = selected.size === 0
  const filterActive = selected.size < values.length

  return (
    <div className="filter-dropdown" ref={dropdownRef}>
      <button
        type="button"
        className={`filter-dropdown-toggle ${filterActive ? 'filter-active' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
      >
        {label}
        <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor" style={{ marginLeft: '4px' }}>
          <path d="M2 4l4 4 4-4H2z"/>
        </svg>
      </button>
      {isOpen && (
        <div className="filter-dropdown-menu">
          <div className="filter-dropdown-actions">
            <button type="button" onClick={onSelectAll} disabled={allSelected}>Check all</button>
            <button type="button" onClick={onClearAll} disabled={noneSelected}>Uncheck all</button>
          </div>
          <div className="filter-dropdown-items">
            {values.map(value => (
              <label key={value} className="filter-dropdown-item">
                <input
                  type="checkbox"
                  checked={selected.has(value)}
                  onChange={() => onToggle(value)}
                />
                <span>{value}</span>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default function RecordTable({ records }: Props) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const getSignerEmail = (record: ProvenanceRecord): string | null => {
    return record.signature?.details?.signerIdentity ?? null
  }

  const getRecordSignerDomain = (record: ProvenanceRecord): string => {
    return getSignerDomain(getSignerEmail(record))
  }

  // Extract unique values for filters
  const uniqueTypes = useMemo(() =>
    [...new Set(records.map(r => r.metadata.dataType))].sort(),
    [records]
  )
  const uniqueDataIds = useMemo(() =>
    [...new Set(records.map(r => r.metadata.dataId))].sort(),
    [records]
  )
  const uniqueSigners = useMemo(() =>
    [...new Set(records.map(r => getRecordSignerDomain(r)))].sort(),
    [records]
  )

  // Initialize filters with all values selected
  const [selectedTypes, setSelectedTypes] = useState<Set<string>>(() => new Set(uniqueTypes))
  const [selectedDataIds, setSelectedDataIds] = useState<Set<string>>(() => new Set(uniqueDataIds))
  const [selectedSigners, setSelectedSigners] = useState<Set<string>>(() => new Set(uniqueSigners))

  // Update selected values when records change (to include new values)
  useEffect(() => {
    setSelectedTypes(prev => {
      const newSet = new Set(prev)
      uniqueTypes.forEach(t => newSet.add(t))
      // Remove values that no longer exist
      prev.forEach(t => { if (!uniqueTypes.includes(t)) newSet.delete(t) })
      return newSet
    })
  }, [uniqueTypes])

  useEffect(() => {
    setSelectedDataIds(prev => {
      const newSet = new Set(prev)
      uniqueDataIds.forEach(id => newSet.add(id))
      prev.forEach(id => { if (!uniqueDataIds.includes(id)) newSet.delete(id) })
      return newSet
    })
  }, [uniqueDataIds])

  useEffect(() => {
    setSelectedSigners(prev => {
      const newSet = new Set(prev)
      uniqueSigners.forEach(s => newSet.add(s))
      prev.forEach(s => { if (!uniqueSigners.includes(s)) newSet.delete(s) })
      return newSet
    })
  }, [uniqueSigners])

  // Filter records based on selections
  const filteredRecords = useMemo(() =>
    records.filter(r =>
      selectedTypes.has(r.metadata.dataType) &&
      selectedDataIds.has(r.metadata.dataId) &&
      selectedSigners.has(getRecordSignerDomain(r))
    ),
    [records, selectedTypes, selectedDataIds, selectedSigners]
  )

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

  const toggleType = (value: string) => {
    setSelectedTypes(prev => {
      const newSet = new Set(prev)
      if (newSet.has(value)) {
        newSet.delete(value)
      } else {
        newSet.add(value)
      }
      return newSet
    })
  }

  const toggleDataId = (value: string) => {
    setSelectedDataIds(prev => {
      const newSet = new Set(prev)
      if (newSet.has(value)) {
        newSet.delete(value)
      } else {
        newSet.add(value)
      }
      return newSet
    })
  }

  const toggleSigner = (value: string) => {
    setSelectedSigners(prev => {
      const newSet = new Set(prev)
      if (newSet.has(value)) {
        newSet.delete(value)
      } else {
        newSet.add(value)
      }
      return newSet
    })
  }

  if (records.length === 0) {
    return <p className="loading">No records found</p>
  }

  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>
              <FilterDropdown
                label="Data ID"
                values={uniqueDataIds}
                selected={selectedDataIds}
                onToggle={toggleDataId}
                onSelectAll={() => setSelectedDataIds(new Set(uniqueDataIds))}
                onClearAll={() => setSelectedDataIds(new Set())}
              />
            </th>
            <th>
              <FilterDropdown
                label="Type"
                values={uniqueTypes}
                selected={selectedTypes}
                onToggle={toggleType}
                onSelectAll={() => setSelectedTypes(new Set(uniqueTypes))}
                onClearAll={() => setSelectedTypes(new Set())}
              />
            </th>
            <th>
              <FilterDropdown
                label="Signed By"
                values={uniqueSigners}
                selected={selectedSigners}
                onToggle={toggleSigner}
                onSelectAll={() => setSelectedSigners(new Set(uniqueSigners))}
                onClearAll={() => setSelectedSigners(new Set())}
              />
            </th>
            <th>Direct Predecessors</th>
            <th style={{ width: '1%', whiteSpace: 'nowrap' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {filteredRecords.length === 0 ? (
            <tr>
              <td colSpan={5} style={{ textAlign: 'center', color: '#666' }}>
                No records match the current filters
              </td>
            </tr>
          ) : (
            filteredRecords.map((record) => (
              <tr key={record.id}>
                <td>{record.metadata.dataId}</td>
                <td>
                  <span className="badge badge-type">{record.metadata.dataType}</span>
                </td>
                <td>
                  <Tooltip text={getSignerEmail(record)}>
                    {getRecordSignerDomain(record)}
                  </Tooltip>
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
                      className="btn btn-icon"
                      title="Download"
                    >
                      {downloadingId === record.id ? (
                        <span>...</span>
                      ) : (
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                          <path d="M8 12l-4-4h2.5V3h3v5H12L8 12z"/>
                          <path d="M14 13v1H2v-1h12z"/>
                        </svg>
                      )}
                    </button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
