import { useTranslation } from 'react-i18next'
import type { TriggerType } from '../../types'

const TRIGGERS: TriggerType[] = [
  'ISSUE_CREATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'ASSIGNEE_CHANGED',
  'COMMENT_ADDED', 'SPRINT_STARTED', 'SPRINT_COMPLETED',
]

interface Props {
  value: TriggerType
  onChange: (v: TriggerType) => void
}

export function TriggerSelector({ value, onChange }: Props) {
  const { t } = useTranslation('automation')
  return (
    <div className="bg-zinc-800/60 border-l-4 border-indigo-500 rounded-r-lg p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="bg-indigo-500 text-white text-xs font-bold px-2 py-0.5 rounded-full">{t('editor.when')}</span>
        <span className="text-zinc-400 text-sm">{t('editor.triggerLabel')}</span>
      </div>
      <select
        value={value}
        onChange={e => onChange(e.target.value as TriggerType)}
        className="w-full bg-zinc-700 border border-zinc-600 text-zinc-200 text-sm rounded-lg px-3 py-2"
      >
        {TRIGGERS.map(tr => <option key={tr} value={tr}>{t(`trigger.${tr}`)}</option>)}
      </select>
    </div>
  )
}
