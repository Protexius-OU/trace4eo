import { useState, useRef, useEffect, useCallback, useId } from 'react'
import './RecordTable.css'
import { Link } from 'react-router-dom'
import type { ProvenanceRecord, FilterOptions, RecordFilters } from '../types/provenance'
import { downloadZip } from '../api/provenanceApi'
import { getSignerDomain } from '../utils/signerIdentity'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { FilterDropdown, DataIdFilter } from './Filters'

const COLUMN_COUNT = 5

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
  const tooltipId = useId()

  const handleShow = () => {
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
        onMouseEnter={handleShow}
        onMouseLeave={() => setShow(false)}
        onFocus={handleShow}
        onBlur={() => setShow(false)}
        aria-describedby={tooltipId}
        tabIndex={0}
        className="tooltip-trigger"
      >
        {children}
      </span>
      {show && (
        <div
          id={tooltipId}
          role="tooltip"
          className="tooltip"
          style={{ left: position.x, top: position.y }}
        >
          {text}
        </div>
      )}
    </>
  )
}

interface AttributesFilterProps {
  value: string
  onChange: (value: string) => void
}

function AttributeBadge({ entryKey, value }: { entryKey: string; value: string }) {
  return (
    <span className="badge badge-attribute" title={`${entryKey}=${value}`}>
      <span className="badge-attribute-key">{entryKey}</span>
      <span className="badge-attribute-value">{value}</span>
    </span>
  )
}

function AttributeBadges({ attributes }: { attributes: Record<string, string> | null }) {
  if (!attributes || Object.keys(attributes).length === 0) {
    return <span className="attribute-empty">—</span>
  }
  const entries = Object.entries(attributes).sort(([a], [b]) => a.localeCompare(b))
  return (
    <div className="attribute-list">
      {entries.map(([key, value]) => (
        <AttributeBadge key={key} entryKey={key} value={value} />
      ))}
    </div>
  )
}

type AttributeChip = { key: string; value: string }

function parseChips(filterValue: string): AttributeChip[] {
  const trimmed = filterValue.trim()
  if (!trimmed) return []
  return trimmed.split(/\s+/).map(token => {
    const eq = token.indexOf('=')
    return { key: token.slice(0, eq), value: token.slice(eq + 1) }
  })
}

function serializeChips(chips: AttributeChip[]): string {
  return chips.map(c => `${c.key}=${c.value}`).join(' ')
}

function AttributesFilter({ value, onChange }: AttributesFilterProps) {
  const [inputValue, setInputValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const chips = parseChips(value)

  useEffect(() => {
    setError(null)
  }, [value])

  const commitInput = () => {
    const token = inputValue.trim()
    if (!token) return
    const eq = token.indexOf('=')
    if (eq <= 0) {
      setError(`Invalid token "${token}": expected key=value`)
      return
    }
    const key = token.slice(0, eq)
    const values = token.slice(eq + 1).split(',').map(v => v.trim()).filter(v => v.length > 0)
    if (values.length === 0) {
      setError(`Invalid token "${token}": value is empty`)
      return
    }
    const seen = new Set(chips.filter(c => c.key === key).map(c => c.value))
    const additions: AttributeChip[] = []
    for (const value of values) {
      if (seen.has(value)) continue
      seen.add(value)
      additions.push({ key, value })
    }
    if (additions.length === 0) {
      setInputValue('')
      setError(null)
      return
    }
    onChange(serializeChips([...chips, ...additions]))
    setInputValue('')
    setError(null)
  }

  const removeChip = (idx: number) => {
    onChange(serializeChips(chips.filter((_, i) => i !== idx)))
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      commitInput()
    } else if (e.key === 'Backspace' && inputValue === '' && chips.length > 0) {
      e.preventDefault()
      removeChip(chips.length - 1)
    }
  }

  return (
    <div
      className={`attribute-chip-input${error ? ' has-error' : ''}`}
      onClick={() => inputRef.current?.focus()}
    >
      {chips.map((chip, i) => (
        <span key={`${chip.key}=${chip.value}-${i}`} className="badge badge-attribute badge-attribute-chip">
          <span className="badge-attribute-key">{chip.key}</span>
          <span className="badge-attribute-value">{chip.value}</span>
          <button
            type="button"
            className="badge-remove"
            onClick={(e) => { e.stopPropagation(); removeChip(i) }}
            aria-label={`Remove ${chip.key}=${chip.value}`}
          >
            ×
          </button>
        </span>
      ))}
      <input
        ref={inputRef}
        type="text"
        placeholder={chips.length === 0 ? 'Filter attributes…' : ''}
        title="Type key=value and press Enter. Same key = OR, different keys = AND. Backspace removes last chip."
        value={inputValue}
        onChange={e => { setInputValue(e.target.value); if (error) setError(null) }}
        onKeyDown={handleKeyDown}
        aria-invalid={error ? 'true' : 'false'}
      />
      {error && <div role="alert" className="filter-error-popover">{error}</div>}
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
  const authFetch = useAuthFetch()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [downloadErrors, setDownloadErrors] = useState<Record<string, string>>({})

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

  const handleAttributesChange = useCallback((value: string) => {
    onFilterChange({ ...filters, attributes: value.trim() || undefined })
  }, [filters, onFilterChange])

  const handleDownload = async (id: string) => {
    setDownloadingId(id)
    setDownloadErrors(prev => { const next = { ...prev }; delete next[id]; return next })
    try {
      await downloadZip(authFetch, id)
    } catch (err) {
      console.error('Download failed:', err)
      setDownloadErrors(prev => ({ ...prev, [id]: 'Download failed' }))
    } finally {
      setDownloadingId(null)
    }
  }

  const hasActiveFilter =
    !!filters.dataId ||
    filters.dataTypes !== undefined ||
    filters.signerIdentities !== undefined ||
    !!filters.attributes

  const clearFilters = () => onFilterChange({})

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
            <th>
              <AttributesFilter
                value={filters.attributes ?? ''}
                onChange={handleAttributesChange}
              />
            </th>
            <th className="th-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          {records.length === 0 ? (
            <tr>
              <td colSpan={COLUMN_COUNT} className="td-empty">
                No records match the current filters
                {hasActiveFilter && (
                  <>
                    {' · '}
                    <button type="button" className="clear-filters" onClick={clearFilters}>
                      Clear filters
                    </button>
                  </>
                )}
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
                  {record.uploaderIdentity && record.uploaderIdentity !== getSignerEmail(record) && (
                    <div className="uploader-via">
                      <Tooltip text={record.uploaderIdentity}>
                        via {getSignerDomain(record.uploaderIdentity)}
                      </Tooltip>
                    </div>
                  )}
                </td>
                <td>
                  <AttributeBadges attributes={record.metadata.attributes ?? null} />
                </td>
                <td>
                  <div className="cell-actions">
                    <Link to={`/records/${record.id}/graph`} className="btn btn-primary">
                      View Details
                    </Link>
                    <button
                      onClick={() => handleDownload(record.id)}
                      disabled={downloadingId === record.id}
                      className={`btn btn-icon${downloadErrors[record.id] ? ' btn-icon-error' : ''}`}
                      title={downloadErrors[record.id] ?? 'Download'}
                      aria-label={downloadErrors[record.id] ?? 'Download'}
                    >
                      {downloadingId === record.id ? (
                        <span>...</span>
                      ) : downloadErrors[record.id] ? (
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                          <path d="M8 8a.75.75 0 0 1-.75-.75v-4.5a.75.75 0 0 1 1.5 0v4.5A.75.75 0 0 1 8 8zm0 3a1 1 0 1 1 0-2 1 1 0 0 1 0 2z"/>
                          <circle cx="8" cy="8" r="7" fill="none" stroke="currentColor" strokeWidth="1.5"/>
                        </svg>
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
