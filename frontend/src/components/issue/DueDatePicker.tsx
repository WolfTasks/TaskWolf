import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'

interface Props {
  value: string | null   // ISO date string "YYYY-MM-DD" or null
  onSave: (date: string | null) => void
  disabled?: boolean
}

export function DueDatePicker({ value, onSave, disabled }: Props) {
  const { t } = useTranslation('issues-fields')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const display = value ? formatDate(value) : t('dueDate.none')

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
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
              {t('dueDate.clear')}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
