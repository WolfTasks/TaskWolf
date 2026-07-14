import { useState, useRef, useEffect } from 'react'
import type { Version } from '@/types'
import { VersionChip } from './VersionChip'
import { useTranslation } from 'react-i18next'

interface Props {
  value: Version[]
  allVersions: Version[]
  onSave: (versionIds: string[]) => void
  onChipClick?: (version: Version) => void
  disabled?: boolean
}

export function VersionSelector({ value, allVersions, onSave, onChipClick, disabled }: Props) {
  const { t } = useTranslation('issues-fields')
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<Version[]>(value)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => { setSelected(value) }, [value])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        if (open) {
          onSave(selected.map(v => v.id))
          setOpen(false)
        }
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open, selected, onSave])

  function toggle(version: Version) {
    setSelected(prev =>
      prev.some(v => v.id === version.id)
        ? prev.filter(v => v.id !== version.id)
        : [...prev, version]
    )
  }

  return (
    <div ref={ref} className="relative">
      <div
        className={`flex flex-wrap gap-1 min-h-[24px] ${disabled ? '' : 'cursor-pointer'}`}
        onClick={() => { if (!disabled) setOpen(o => !o) }}
      >
        {selected.length === 0
          ? <span className="text-sm text-gray-500 hover:text-gray-300">{t('none')}</span>
          : selected.map(v => (
            <VersionChip
              key={v.id}
              version={v}
              onClick={onChipClick ? () => onChipClick(v) : undefined}
            />
          ))
        }
      </div>
      {open && (
        <div className="absolute z-50 top-7 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-52 max-h-64 overflow-y-auto">
          {allVersions.length === 0 && (
            <p className="px-3 py-2 text-sm text-gray-500">{t('version.empty')}</p>
          )}
          {allVersions.map(v => (
            <button
              key={v.id}
              onClick={() => toggle(v)}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <span className={`w-3 h-3 rounded-full flex-shrink-0 border ${selected.some(s => s.id === v.id) ? 'border-white bg-indigo-500' : 'border-gray-500 bg-transparent'}`} />
              {v.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
