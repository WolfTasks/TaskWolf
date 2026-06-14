import type { TriggerType } from '../../types'

const TRIGGERS: { value: TriggerType; label: string }[] = [
  { value: 'ISSUE_CREATED', label: 'Issue erstellt' },
  { value: 'STATUS_CHANGED', label: 'Status geändert' },
  { value: 'PRIORITY_CHANGED', label: 'Priorität geändert' },
  { value: 'ASSIGNEE_CHANGED', label: 'Assignee geändert' },
  { value: 'COMMENT_ADDED', label: 'Kommentar hinzugefügt' },
  { value: 'SPRINT_STARTED', label: 'Sprint gestartet' },
  { value: 'SPRINT_COMPLETED', label: 'Sprint abgeschlossen' },
]

interface Props {
  value: TriggerType
  onChange: (v: TriggerType) => void
}

export function TriggerSelector({ value, onChange }: Props) {
  return (
    <div className="bg-zinc-800/60 border-l-4 border-indigo-500 rounded-r-lg p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="bg-indigo-500 text-white text-xs font-bold px-2 py-0.5 rounded-full">WHEN</span>
        <span className="text-zinc-400 text-sm">Trigger</span>
      </div>
      <select
        value={value}
        onChange={e => onChange(e.target.value as TriggerType)}
        className="w-full bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded-lg px-3 py-2"
      >
        {TRIGGERS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
      </select>
    </div>
  )
}
