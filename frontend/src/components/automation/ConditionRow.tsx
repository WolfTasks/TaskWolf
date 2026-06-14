import type { RuleCondition, ConditionType } from '../../types'

const TYPES: { value: ConditionType; label: string }[] = [
  { value: 'ISSUE_TYPE', label: 'Issue-Typ' },
  { value: 'PRIORITY', label: 'Priorität' },
  { value: 'ASSIGNEE', label: 'Assignee' },
  { value: 'STATUS', label: 'Status' },
  { value: 'STORY_POINTS', label: 'Story Points' },
]

const OPERATORS = ['IS', 'IS_NOT', 'CONTAINS', 'GT', 'LT']

interface Props {
  condition: RuleCondition
  onChange: (c: RuleCondition) => void
  onRemove: () => void
}

export function ConditionRow({ condition, onChange, onRemove }: Props) {
  return (
    <div className="flex gap-2 items-center">
      <select
        value={condition.type}
        onChange={e => onChange({ ...condition, type: e.target.value as ConditionType })}
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      >
        {TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
      </select>
      <select
        value={condition.operator}
        onChange={e => onChange({ ...condition, operator: e.target.value as RuleCondition['operator'] })}
        className="w-24 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      >
        {OPERATORS.map(op => <option key={op} value={op}>{op}</option>)}
      </select>
      <input
        value={condition.params.value ?? ''}
        onChange={e => onChange({ ...condition, params: { value: e.target.value } })}
        placeholder="Wert"
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      />
      <button onClick={onRemove} className="text-zinc-500 hover:text-red-400 px-1">✕</button>
    </div>
  )
}
