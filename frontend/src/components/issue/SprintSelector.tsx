import { useState, useRef, useEffect } from 'react'
import type { Sprint } from '@/types'

interface Props {
  value: string | null        // sprintName
  sprintId: string | null
  sprints: Sprint[]
  onSave: (sprintId: string | null) => void
  disabled?: boolean
}

export function SprintSelector({ value, sprintId, sprints, onSave, disabled }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const activeSprints = sprints.filter(s => s.status === 'ACTIVE' || s.status === 'PLANNED')

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
      >
        {value ?? 'No sprint'}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-40">
          {sprintId && (
            <button
              onClick={() => { onSave(null); setOpen(false) }}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700"
            >
              No sprint
            </button>
          )}
          {activeSprints.map(s => (
            <button
              key={s.id}
              onClick={() => { onSave(s.id); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${s.id === sprintId ? 'font-bold text-white' : ''}`}
            >
              {s.name}
            </button>
          ))}
          {activeSprints.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-600">No active sprints</p>
          )}
        </div>
      )}
    </div>
  )
}
