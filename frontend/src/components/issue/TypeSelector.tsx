import { useState, useRef, useEffect } from 'react'

const TYPES = ['EPIC', 'STORY', 'BUG', 'TASK', 'SUBTASK'] as const
type IssueType = typeof TYPES[number]

interface Props {
  value: IssueType
  onSave: (value: IssueType) => void
}

export function TypeSelector({ value, onSave }: Props) {
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
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {value}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {TYPES.map(t => (
            <button
              key={t}
              onClick={() => { onSave(t); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${t === value ? 'font-bold text-white' : ''}`}
            >
              {t}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
