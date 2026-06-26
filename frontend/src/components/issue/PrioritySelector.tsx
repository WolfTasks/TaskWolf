import { useState, useRef, useEffect } from 'react'

const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const
type Priority = typeof PRIORITIES[number]

const colors: Record<Priority, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props {
  value: Priority
  onSave: (value: Priority) => void
}

export function PrioritySelector({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className={`text-sm font-medium ${colors[value]} cursor-pointer hover:underline`}
      >
        {value}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {PRIORITIES.map(p => (
            <button
              key={p}
              onClick={() => { onSave(p); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm ${colors[p]} hover:bg-gray-700 ${p === value ? 'font-bold' : ''}`}
            >
              {p}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
