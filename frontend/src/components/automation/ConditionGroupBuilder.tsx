import type { RuleConditionGroup, RuleCondition, GroupLogic } from '../../types'
import { ConditionRow } from './ConditionRow'

interface Props {
  group: RuleConditionGroup
  onChange: (g: RuleConditionGroup) => void
  depth?: number
}

const emptyCondition = (): RuleCondition => ({ type: 'PRIORITY', operator: 'IS', params: { value: '' } })
const emptyGroup = (logic: GroupLogic = 'AND'): RuleConditionGroup => ({ logic, conditions: [], childGroups: [] })

export function ConditionGroupBuilder({ group, onChange, depth = 0 }: Props) {
  const setLogic = (logic: GroupLogic) => onChange({ ...group, logic })

  const addCondition = () => onChange({ ...group, conditions: [...group.conditions, emptyCondition()] })
  const updateCondition = (i: number, c: RuleCondition) =>
    onChange({ ...group, conditions: group.conditions.map((x, idx) => idx === i ? c : x) })
  const removeCondition = (i: number) =>
    onChange({ ...group, conditions: group.conditions.filter((_, idx) => idx !== i) })

  const addChildGroup = () => onChange({ ...group, childGroups: [...group.childGroups, emptyGroup()] })
  const updateChildGroup = (i: number, g: RuleConditionGroup) =>
    onChange({ ...group, childGroups: group.childGroups.map((x, idx) => idx === i ? g : x) })
  const removeChildGroup = (i: number) =>
    onChange({ ...group, childGroups: group.childGroups.filter((_, idx) => idx !== i) })

  return (
    <div className={`border border-dashed border-zinc-600 rounded-lg p-3 flex flex-col gap-2 ${depth > 0 ? 'ml-4' : ''}`}>
      <div className="flex gap-2 items-center">
        {(['AND', 'OR'] as GroupLogic[]).map(l => (
          <button key={l} onClick={() => setLogic(l)}
            className={`text-xs px-3 py-1 rounded font-medium transition-colors
              ${group.logic === l ? 'bg-indigo-600 text-white' : 'bg-zinc-700 text-zinc-400 hover:bg-zinc-600'}`}
          >{l}</button>
        ))}
        <div className="flex-1" />
        <button onClick={addCondition} className="text-xs text-zinc-400 hover:text-zinc-200">+ Bedingung</button>
        {depth < 2 && (
          <button onClick={addChildGroup} className="text-xs text-zinc-400 hover:text-zinc-200">+ Gruppe</button>
        )}
      </div>
      {group.conditions.map((c, i) => (
        <ConditionRow key={i} condition={c} onChange={nc => updateCondition(i, nc)} onRemove={() => removeCondition(i)} />
      ))}
      {group.childGroups.map((g, i) => (
        <div key={i} className="relative">
          <ConditionGroupBuilder group={g} onChange={ng => updateChildGroup(i, ng)} depth={depth + 1} />
          <button onClick={() => removeChildGroup(i)} className="absolute top-1 right-2 text-zinc-500 hover:text-red-400 text-xs">✕</button>
        </div>
      ))}
    </div>
  )
}
