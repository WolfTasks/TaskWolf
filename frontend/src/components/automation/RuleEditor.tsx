import { useState } from 'react'
import type { TriggerType, RuleConditionGroup, RuleAction } from '../../types'
import { TriggerSelector } from './TriggerSelector'
import { ConditionGroupBuilder } from './ConditionGroupBuilder'
import { ActionList } from './ActionList'

interface Props {
  onSave: (data: { name: string; triggerType: TriggerType; rootGroup: RuleConditionGroup; actions: RuleAction[] }) => void
  onCancel: () => void
  initialName?: string
}

export function RuleEditor({ onSave, onCancel, initialName = '' }: Props) {
  const [name, setName] = useState(initialName)
  const [triggerType, setTriggerType] = useState<TriggerType>('STATUS_CHANGED')
  const [rootGroup, setRootGroup] = useState<RuleConditionGroup>({ logic: 'AND', conditions: [], childGroups: [] })
  const [actions, setActions] = useState<RuleAction[]>([])

  return (
    <div className="flex flex-col gap-5 max-w-2xl">
      <div>
        <label className="text-xs uppercase text-zinc-400 mb-1 block">Regelname</label>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="z.B. CRITICAL Issues auto-assign"
          className="w-full bg-zinc-800 border border-zinc-700 text-zinc-200 rounded-lg px-3 py-2 text-sm"
        />
      </div>
      <TriggerSelector value={triggerType} onChange={setTriggerType} />
      <div className="bg-zinc-800/60 border-l-4 border-amber-500 rounded-r-lg p-4">
        <div className="flex items-center gap-2 mb-3">
          <span className="bg-amber-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">IF</span>
          <span className="text-zinc-400 text-sm">Bedingungen</span>
        </div>
        <ConditionGroupBuilder group={rootGroup} onChange={setRootGroup} />
      </div>
      <div className="bg-zinc-800/60 border-l-4 border-emerald-500 rounded-r-lg p-4">
        <div className="flex items-center gap-2 mb-3">
          <span className="bg-emerald-500 text-zinc-900 text-xs font-bold px-2 py-0.5 rounded-full">THEN</span>
          <span className="text-zinc-400 text-sm">Actions (in Reihenfolge)</span>
        </div>
        <ActionList actions={actions} onChange={setActions} />
      </div>
      <div className="flex gap-3 justify-end pt-2 border-t border-zinc-800">
        <button onClick={onCancel} className="bg-zinc-700 text-zinc-300 text-sm rounded-lg px-4 py-2 hover:bg-zinc-600">Abbrechen</button>
        <button
          onClick={() => onSave({ name, triggerType, rootGroup, actions })}
          disabled={!name.trim() || actions.length === 0}
          className="bg-indigo-600 text-white text-sm rounded-lg px-4 py-2 hover:bg-indigo-500 disabled:opacity-40"
        >
          Regel speichern
        </button>
      </div>
    </div>
  )
}
