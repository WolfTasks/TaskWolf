import { useState, useRef, useEffect } from 'react'
import type { Label } from '@/types'
import { LabelChip } from './LabelChip'
import { useCreateLabel } from '@/hooks/useLabels'

const PALETTE = [
  '#e11d48','#f97316','#eab308','#22c55e',
  '#14b8a6','#3b82f6','#8b5cf6','#ec4899',
  '#64748b','#0ea5e9','#84cc16','#f43f5e',
]

interface Props {
  projectKey: string
  value: Label[]          // currently assigned labels
  allLabels: Label[]      // all project labels
  onSave: (labelIds: string[]) => void
  onChipClick?: (label: Label) => void
  disabled?: boolean
}

export function LabelSelector({ projectKey, value, allLabels, onSave, onChipClick, disabled }: Props) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Label[]>(value)
  const ref = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const createLabel = useCreateLabel(projectKey)

  useEffect(() => { setSelected(value) }, [value])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        if (open) {
          onSave(selected.map(l => l.id))
          setOpen(false)
          setSearch('')
        }
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open, selected, onSave])

  useEffect(() => { if (open) inputRef.current?.focus() }, [open])

  const filtered = allLabels.filter(l =>
    l.name.toLowerCase().includes(search.toLowerCase())
  )
  const exactMatch = allLabels.some(l => l.name.toLowerCase() === search.toLowerCase())

  function toggle(label: Label) {
    setSelected(prev =>
      prev.some(l => l.id === label.id)
        ? prev.filter(l => l.id !== label.id)
        : [...prev, label]
    )
  }

  async function handleCreate() {
    const newLabel = await createLabel.mutateAsync({
      name: search.trim(),
      color: PALETTE[allLabels.length % PALETTE.length],
    })
    setSelected(prev => [...prev, newLabel])
    setSearch('')
  }

  return (
    <div ref={ref} className="relative">
      <div
        className={`flex flex-wrap gap-1 min-h-[24px] ${disabled ? '' : 'cursor-pointer'}`}
        onClick={() => { if (!disabled) setOpen(o => !o) }}
      >
        {selected.length === 0
          ? <span className="text-sm text-gray-500 hover:text-gray-300">None</span>
          : selected.map(l => (
            <LabelChip
              key={l.id}
              label={l}
              onClick={onChipClick ? () => onChipClick(l) : undefined}
            />
          ))
        }
      </div>
      {open && (
        <div className="absolute z-50 top-7 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-52 max-h-64 overflow-y-auto">
          <div className="px-2 pb-1">
            <input
              ref={inputRef}
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search labels…"
              className="w-full bg-gray-700 text-sm text-white rounded px-2 py-1 outline-none"
            />
          </div>
          {filtered.map(l => (
            <button
              key={l.id}
              onClick={() => toggle(l)}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <span className={`w-3 h-3 rounded-full flex-shrink-0 ${selected.some(s => s.id === l.id) ? 'ring-2 ring-white' : ''}`}
                    style={{ backgroundColor: l.color }} />
              <LabelChip label={l} />
            </button>
          ))}
          {search.trim() && !exactMatch && (
            <button
              onClick={handleCreate}
              disabled={createLabel.isPending}
              className="w-full text-left px-3 py-1.5 text-sm text-blue-400 hover:bg-gray-700"
            >
              + Create label "{search.trim()}"
            </button>
          )}
        </div>
      )}
    </div>
  )
}
