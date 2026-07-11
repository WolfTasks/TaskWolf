import { useState, useRef, useEffect } from 'react'
import type { User } from '@/types'

interface Props {
  value: string | null        // assigneeName
  assigneeId: string | null
  members: User[]
  onSave: (userId: string | null) => void
  disabled?: boolean
}

export function AssigneeSelector({ value, assigneeId, members, onSave, disabled }: Props) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const ref = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) { setOpen(false); setSearch('') }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  useEffect(() => { if (open) inputRef.current?.focus() }, [open])

  const filtered = members.filter(m =>
    m.displayName.toLowerCase().includes(search.toLowerCase()) ||
    m.email.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
      >
        {value ?? 'Unassigned'}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-48">
          <div className="px-2 pb-1">
            <input
              ref={inputRef}
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search members…"
              className="w-full bg-gray-700 text-sm text-white rounded px-2 py-1 outline-none"
            />
          </div>
          {assigneeId && (
            <button
              onClick={() => { onSave(null); setOpen(false); setSearch('') }}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700"
            >
              Unassign
            </button>
          )}
          {filtered.map(m => (
            <button
              key={m.id}
              onClick={() => { onSave(m.id); setOpen(false); setSearch('') }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${m.id === assigneeId ? 'font-bold text-white' : ''}`}
            >
              {m.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
