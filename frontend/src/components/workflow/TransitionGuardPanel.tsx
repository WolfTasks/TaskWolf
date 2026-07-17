import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { WorkflowTransition, TransitionGuard } from '../../types'

interface Props {
  transition: WorkflowTransition
  onSave: (guards: TransitionGuard[]) => void
  onDelete: () => void
  onClose: () => void
}

const ISSUE_FIELDS = ['title', 'description', 'assigneeId', 'storyPoints', 'dueDate']
const ROLES = ['ADMIN', 'MEMBER']

export function TransitionGuardPanel({ transition, onSave, onDelete, onClose }: Props) {
  const { t } = useTranslation('workflow')
  const initial: TransitionGuard[] = transition.guards ? JSON.parse(transition.guards) : []
  const [guards, setGuards] = useState<TransitionGuard[]>(initial)

  const addRequiredField = () =>
    setGuards(g => [...g, { type: 'REQUIRED_FIELD', field: 'storyPoints' }])

  const addRoleRestriction = () =>
    setGuards(g => [...g, { type: 'ROLE_RESTRICTION', roles: ['ADMIN'] }])

  const remove = (i: number) => setGuards(g => g.filter((_, idx) => idx !== i))

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative z-10 w-80 bg-zinc-900 border-l border-zinc-700 p-6 flex flex-col gap-4 overflow-y-auto">
        <h3 className="font-semibold text-zinc-100">{t('guards.title')}</h3>
        <div className="flex flex-col gap-3">
          {guards.map((g, i) => (
            <div key={i} className="bg-zinc-800 rounded-lg p-3 flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="text-xs text-zinc-400 uppercase">{t(`guards.type.${g.type}`)}</span>
                <button onClick={() => remove(i)} className="text-zinc-500 hover:text-red-400 text-xs">✕</button>
              </div>
              {g.type === 'REQUIRED_FIELD' && (
                <select
                  value={g.field}
                  onChange={e => setGuards(gs => gs.map((x, idx) => idx === i ? { ...x, field: e.target.value } : x))}
                  className="bg-zinc-700 text-zinc-200 text-sm rounded px-2 py-1"
                >
                  {ISSUE_FIELDS.map(f => <option key={f} value={f}>{t(`guards.field.${f}`)}</option>)}
                </select>
              )}
              {g.type === 'ROLE_RESTRICTION' && (
                <div className="flex gap-2">
                  {ROLES.map(r => (
                    <label key={r} className="flex items-center gap-1 text-sm text-zinc-300">
                      <input type="checkbox"
                        checked={g.roles?.includes(r) ?? false}
                        onChange={e => setGuards(gs => gs.map((x, idx) => idx === i
                          ? { ...x, roles: e.target.checked ? [...(x.roles ?? []), r] : (x.roles ?? []).filter(v => v !== r) }
                          : x))}
                      /> {t(`guards.role.${r}`)}
                    </label>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="flex gap-2">
          <button onClick={addRequiredField} className="text-xs bg-zinc-700 text-zinc-300 rounded px-2 py-1 hover:bg-zinc-600">+ {t('guards.addRequiredField')}</button>
          <button onClick={addRoleRestriction} className="text-xs bg-zinc-700 text-zinc-300 rounded px-2 py-1 hover:bg-zinc-600">+ {t('guards.addRoleRestriction')}</button>
        </div>
        <div className="flex gap-2 pt-2 border-t border-zinc-700">
          <button onClick={() => onSave(guards)} className="flex-1 bg-indigo-600 text-white text-sm rounded px-3 py-2 hover:bg-indigo-500">{t('guards.save')}</button>
          <button onClick={onDelete} className="bg-red-900 text-red-300 text-sm rounded px-3 py-2 hover:bg-red-800">{t('guards.delete')}</button>
        </div>
      </div>
    </div>
  )
}
