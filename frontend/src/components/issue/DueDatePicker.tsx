import { useState, useRef, useEffect } from 'react'

interface Props {
  value: string | null   // ISO date string "YYYY-MM-DD" or null
  onSave: (date: string | null) => void
}

export function DueDatePicker({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const display = value ? new Date(value).toLocaleDateString() : 'No due date'

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {display}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg p-3 min-w-44">
          <input
            type="date"
            defaultValue={value ?? ''}
            onChange={e => { onSave(e.target.value || null); setOpen(false) }}
            className="bg-gray-700 text-white text-sm rounded px-2 py-1 outline-none w-full"
          />
          {value && (
            <button
              onClick={() => { onSave(null); setOpen(false) }}
              className="mt-2 text-xs text-gray-500 hover:text-red-400 w-full text-left"
            >
              Clear due date
            </button>
          )}
        </div>
      )}
    </div>
  )
}
