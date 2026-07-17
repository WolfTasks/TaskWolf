import { useTranslation } from 'react-i18next'
import type { RuleCondition, ConditionType } from '../../types'

const TYPES: ConditionType[] = ['ISSUE_TYPE', 'PRIORITY', 'ASSIGNEE', 'STATUS', 'STORY_POINTS']

const OPERATORS = ['IS', 'IS_NOT', 'CONTAINS', 'GT', 'LT']

interface Props {
  condition: RuleCondition
  onChange: (c: RuleCondition) => void
  onRemove: () => void
}

export function ConditionRow({ condition, onChange, onRemove }: Props) {
  const { t } = useTranslation('automation')
  return (
    <div className="flex gap-2 items-center">
      <select
        value={condition.type}
        onChange={e => onChange({ ...condition, type: e.target.value as ConditionType })}
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      >
        {TYPES.map(ct => <option key={ct} value={ct}>{t(`condition.${ct}`)}</option>)}
      </select>
      <select
        value={condition.operator}
        onChange={e => onChange({ ...condition, operator: e.target.value as RuleCondition['operator'] })}
        className="w-24 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      >
        {OPERATORS.map(op => <option key={op} value={op}>{t(`operator.${op}`)}</option>)}
      </select>
      <input
        value={condition.params.value ?? ''}
        onChange={e => onChange({ ...condition, params: { value: e.target.value } })}
        placeholder={t('condition.valuePlaceholder')}
        className="flex-1 bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded px-2 py-1.5"
      />
      <button onClick={onRemove} className="text-zinc-500 hover:text-red-400 px-1">✕</button>
    </div>
  )
}
