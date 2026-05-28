import { useState, useRef, useEffect, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown } from 'lucide-react'

interface FilterDropdownProps {
  label: string
  values: string[]
  displayValues?: Map<string, string>
  selected: Set<string>
  onToggle: (value: string) => void
  onSelectAll: () => void
  onClearAll: () => void
}

export function FilterDropdown({ label, values, displayValues, selected, onToggle, onSelectAll, onClearAll }: FilterDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [menuPos, setMenuPos] = useState({ left: 0, top: 0 })
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    if (!isOpen || !triggerRef.current) return
    const rect = triggerRef.current.getBoundingClientRect()
    setMenuPos({ left: rect.left, top: rect.bottom + 4 })
  }, [isOpen])

  useEffect(() => {
    if (!isOpen) return
    const handleMouseDown = (event: MouseEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target)) return
      if (menuRef.current?.contains(target)) return
      setIsOpen(false)
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  useEffect(() => {
    if (!isOpen) return
    // capture so we catch scrolls on nested scrollers (e.g. the modal table),
    // but ignore scrolling within the menu's own list so it stays open
    const handleScroll = (event: Event) => {
      if (menuRef.current?.contains(event.target as Node)) return
      setIsOpen(false)
    }
    const close = () => setIsOpen(false)
    window.addEventListener('scroll', handleScroll, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', handleScroll, true)
      window.removeEventListener('resize', close)
    }
  }, [isOpen])

  const allSelected = selected.size === values.length
  const noneSelected = selected.size === 0
  const filterActive = !allSelected
  const toggleLabel = allSelected ? label : `${label} (${selected.size}/${values.length})`

  return (
    <div className="filter-dropdown">
      <button
        ref={triggerRef}
        type="button"
        className={`filter-dropdown-toggle ${filterActive ? 'filter-active' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        {toggleLabel}
        <ChevronDown size={12} className="dropdown-arrow" aria-hidden="true" />
      </button>
      {isOpen && createPortal(
        <div
          ref={menuRef}
          className="filter-dropdown-menu"
          style={{ position: 'fixed', left: menuPos.left, top: menuPos.top, zIndex: 1100 }}
        >
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
        </div>,
        document.body
      )}
    </div>
  )
}

interface DataIdFilterProps {
  value: string
  onChange: (value: string) => void
}

export function DataIdFilter({ value, onChange }: DataIdFilterProps) {
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
        placeholder="Search Data ID..."
        title="Press Enter to search"
        value={localValue}
        onChange={e => setLocalValue(e.target.value)}
        onKeyDown={handleKeyDown}
        className="filter-text-input"
      />
    </div>
  )
}
