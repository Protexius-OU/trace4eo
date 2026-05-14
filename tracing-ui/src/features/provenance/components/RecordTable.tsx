import { useState, useRef, useEffect, useCallback } from 'react'
import './RecordTable.css'
import { Link } from 'react-router-dom'
import type { ProvenanceRecord, FilterOptions, RecordFilters } from '../types/provenance'
import { downloadZip } from '../api/provenanceApi'
import { getSignerDomain } from '../utils/signerIdentity'

interface Props {
  records: ProvenanceRecord[]
  filterOptions: FilterOptions
  filters: RecordFilters
  onFilterChange: (filters: RecordFilters) => void
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
  displayValues?: Map<string, string>
  selected: Set<string>
  onToggle: (value: string) => void
  onSelectAll: () => void
  onClearAll: () => void
}

function FilterDropdown({ label, values, displayValues, selected, onToggle, onSelectAll, onClearAll }: FilterDropdownProps) {
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
  const filterActive = !allSelected && !noneSelected

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
                <span>{displayValues?.get(value) ?? value}</span>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

interface DataIdFilterProps {
  value: string
  onChange: (value: string) => void
}

function DataIdFilter({ value, onChange }: DataIdFilterProps) {
  const [localValue, setLocalValue] = useState(value)

  useEffect(() => {
    setLocalValue(value)
  }, [value])

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      onChange(localValue)
    }
  }

  return (
    <div className="filter-dropdown">
      <input
        type="text"
        placeholder="Search Data ID (press Enter)..."
        value={localValue}
        onChange={e => setLocalValue(e.target.value)}
        onKeyDown={handleKeyDown}
        className="filter-text-input"
      />
    </div>
  )
}

function useCheckboxFilter(
  filterKey: 'dataTypes' | 'signerIdentities',
  allValues: string[],
  filters: RecordFilters,
  onFilterChange: (f: RecordFilters) => void
) {
  const selected = new Set(filters[filterKey] ?? allValues)

  const toggle = useCallback((value: string) => {
    const current = new Set(filters[filterKey] ?? allValues)
    if (current.has(value)) {
      current.delete(value)
    } else {
      current.add(value)
    }
    const updated = current.size === allValues.length ? undefined : [...current]
    onFilterChange({ ...filters, [filterKey]: updated })
  }, [filterKey, allValues, filters, onFilterChange])

  const selectAll = useCallback(() => {
    onFilterChange({ ...filters, [filterKey]: undefined })
  }, [filterKey, filters, onFilterChange])

  const clearAll = useCallback(() => {
    onFilterChange({ ...filters, [filterKey]: [] })
  }, [filterKey, filters, onFilterChange])

  return { selected, toggle, selectAll, clearAll }
}

export default function RecordTable({ records, filterOptions, filters, onFilterChange }: Props) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const getSignerEmail = (record: ProvenanceRecord): string | null => {
    return record.signature?.details?.signerIdentity ?? null
  }

  const getRecordSignerDomain = (record: ProvenanceRecord): string => {
    return getSignerDomain(getSignerEmail(record))
  }

  const signerDisplayValues = new Map(
    filterOptions.signerIdentities.map(email => [email, getSignerDomain(email)])
  )

  const typeFilter = useCheckboxFilter('dataTypes', filterOptions.dataTypes, filters, onFilterChange)
  const signerFilter = useCheckboxFilter('signerIdentities', filterOptions.signerIdentities, filters, onFilterChange)

  const handleDataIdChange = useCallback((value: string) => {
    onFilterChange({ ...filters, dataId: value || undefined })
  }, [filters, onFilterChange])

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

  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>
              <DataIdFilter
                value={filters.dataId ?? ''}
                onChange={handleDataIdChange}
              />
            </th>
            <th>
              <FilterDropdown
                label="Type"
                values={filterOptions.dataTypes}
                selected={typeFilter.selected}
                onToggle={typeFilter.toggle}
                onSelectAll={typeFilter.selectAll}
                onClearAll={typeFilter.clearAll}
              />
            </th>
            <th>
              <FilterDropdown
                label="Signed By"
                values={filterOptions.signerIdentities}
                displayValues={signerDisplayValues}
                selected={signerFilter.selected}
                onToggle={signerFilter.toggle}
                onSelectAll={signerFilter.selectAll}
                onClearAll={signerFilter.clearAll}
              />
            </th>
            <th>Uploaded By</th>
            <th style={{ width: '1%', whiteSpace: 'nowrap' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {records.length === 0 ? (
            <tr>
              <td colSpan={5} style={{ textAlign: 'center', color: '#666' }}>
                No records match the current filters
              </td>
            </tr>
          ) : (
            records.map((record) => (
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
                <td>
                  <Tooltip text={record.uploaderIdentity ?? null}>
                    {getSignerDomain(record.uploaderIdentity ?? null)}
                  </Tooltip>
                </td>
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
