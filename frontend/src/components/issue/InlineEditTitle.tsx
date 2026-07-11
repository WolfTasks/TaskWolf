import { useState, useRef, useEffect } from 'react'

interface Props {
  value: string
  onSave: (value: string) => void
  disabled?: boolean
}

export function InlineEditTitle({ value, onSave, disabled }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { if (editing) inputRef.current?.focus() }, [editing])
  useEffect(() => { setDraft(value) }, [value])

  function commit() {
    if (disabled) { setEditing(false); return }
    const trimmed = draft.trim()
    if (trimmed && trimmed !== value) onSave(trimmed)
    setEditing(false)
  }

  if (editing) {
    return (
      <input
        ref={inputRef}
        value={draft}
        onChange={e => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={e => {
          if (e.key === 'Enter') commit()
          if (e.key === 'Escape') { setDraft(value); setEditing(false) }
        }}
        className="w-full text-2xl font-bold bg-transparent border-b border-blue-500 text-white outline-none mb-6"
      />
    )
  }

  return (
    <h1
      onClick={() => { if (!disabled) setEditing(true) }}
      className={`text-2xl font-bold text-white mb-6 rounded px-1 -mx-1 ${disabled ? '' : 'cursor-pointer hover:bg-gray-800'}`}
    >
      {value}
    </h1>
  )
}
