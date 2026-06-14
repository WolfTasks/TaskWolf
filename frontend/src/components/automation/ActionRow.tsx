import type { RuleAction, ActionType } from '../../types'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

const ACTION_TYPES: { value: ActionType; label: string; paramKey: string; placeholder: string }[] = [
  { value: 'SET_STATUS', label: 'Status setzen', paramKey: 'statusId', placeholder: 'Status-ID' },
  { value: 'SET_ASSIGNEE', label: 'Assignee setzen', paramKey: 'assigneeId', placeholder: 'User-ID' },
  { value: 'SET_PRIORITY', label: 'Priorität setzen', paramKey: 'priority', placeholder: 'CRITICAL | HIGH | MEDIUM | LOW' },
  { value: 'SEND_NOTIFICATION', label: 'Notification senden', paramKey: 'message', placeholder: 'Nachricht' },
  { value: 'CREATE_COMMENT', label: 'Kommentar erstellen', paramKey: 'body', placeholder: 'Kommentar-Text' },
  { value: 'CREATE_SUBTASK', label: 'Subtask erstellen', paramKey: 'title', placeholder: 'Subtask-Titel' },
]

interface Props {
  action: RuleAction
  onChange: (a: RuleAction) => void
  onRemove: () => void
}

export function ActionRow({ action, onChange, onRemove }: Props) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: action.position })
  const style = { transform: CSS.Transform.toString(transform), transition }
  const meta = ACTION_TYPES.find(a => a.value === action.type) ?? ACTION_TYPES[0]

  return (
    <div ref={setNodeRef} style={style} className="flex gap-2 items-center bg-zinc-900 rounded-lg px-3 py-2">
      <span {...attributes} {...listeners} className="text-zinc-600 cursor-grab text-sm">⠿</span>
      <span className="bg-zinc-700 text-zinc-400 text-xs rounded px-1.5 py-0.5">{action.position + 1}</span>
      <select
        value={action.type}
        onChange={e => onChange({ ...action, type: e.target.value as ActionType, params: {} })}
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      >
        {ACTION_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
      </select>
      <input
        value={action.params[meta.paramKey] ?? ''}
        onChange={e => onChange({ ...action, params: { [meta.paramKey]: e.target.value } })}
        placeholder={meta.placeholder}
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      />
      <button onClick={onRemove} className="text-zinc-500 hover:text-red-400 px-1">✕</button>
    </div>
  )
}
