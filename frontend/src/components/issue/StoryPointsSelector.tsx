import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

const POINTS = [1, 2, 3, 5, 8, 13, 21] as const

interface Props {
  value: number | null | undefined
  onSave: (value: number | null) => void
  disabled?: boolean
}

export function StoryPointsSelector({ value, onSave, disabled }: Props) {
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
        className={`text-sm ${disabled ? '' : 'cursor-pointer hover:underline'} ${value != null ? 'text-white' : 'text-gray-500'}`}
      >
        {value != null ? value : t('storyPoints.set')}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {POINTS.map(p => (
            <button
              key={p}
              onClick={() => { onSave(p); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${p === value ? 'font-bold text-white' : ''}`}
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => { onSave(null); setOpen(false) }}
            className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700 border-t border-gray-700"
          >
            — {t('storyPoints.clear')}
          </button>
        </div>
      )}
    </div>
  )
}
