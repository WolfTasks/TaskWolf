import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

const TYPES = ['EPIC', 'STORY', 'BUG', 'TASK', 'SUBTASK'] as const
type IssueType = typeof TYPES[number]

interface Props {
  value: IssueType
  onSave: (value: IssueType) => void
  disabled?: boolean
}

export function TypeSelector({ value, onSave, disabled }: Props) {
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

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => { if (!disabled) setOpen(o => !o) }}
        disabled={disabled}
        className={`text-sm text-gray-300 ${disabled ? '' : 'cursor-pointer hover:underline'}`}
      >
        {t(`type.${value}`)}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {TYPES.map(ty => (
            <button
              key={ty}
              onClick={() => { onSave(ty); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${ty === value ? 'font-bold text-white' : ''}`}
            >
              {t(`type.${ty}`)}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
