import { useParams, useNavigate } from 'react-router-dom'
import { useAutomationRules, useToggleRule, useDeleteRule } from '../../hooks/useAutomation'

export function AutomationPage() {
  const { key } = useParams<{ key: string }>()
  const { data, isLoading } = useAutomationRules(key!)
  const toggle = useToggleRule(key!)
  const remove = useDeleteRule(key!)
  const navigate = useNavigate()

  if (isLoading) return <div className="p-6 text-zinc-400">Lade Regeln...</div>

  return (
    <div className="p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-100">Automation</h1>
        <button
          onClick={() => navigate(`/p/${key}/automation/new`)}
          className="bg-indigo-600 text-white text-sm rounded-lg px-4 py-2 hover:bg-indigo-500"
        >
          + Neue Regel
        </button>
      </div>
      {(!data?.content || data.content.length === 0) && (
        <div className="text-zinc-400 text-sm text-center py-12 border border-dashed border-zinc-700 rounded-xl">
          Noch keine Automation-Regeln. Klicke auf "+ Neue Regel" um zu starten.
        </div>
      )}
      {data?.content.map(rule => (
        <div key={rule.id} className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex items-center gap-4">
          <div className="flex-1">
            <div className="font-medium text-zinc-100 text-sm">{rule.name}</div>
            <div className="text-xs text-zinc-400 mt-0.5">{rule.triggerType.replace(/_/g, ' ')}</div>
          </div>
          <button
            onClick={() => toggle.mutate(rule.id)}
            className={`text-xs rounded-full px-3 py-1 font-medium transition-colors
              ${rule.enabled ? 'bg-emerald-900/50 text-emerald-300' : 'bg-zinc-800 text-zinc-500'}`}
          >
            {rule.enabled ? 'Aktiv' : 'Inaktiv'}
          </button>
          <button onClick={() => navigate(`/p/${key}/automation/${rule.id}/edit`)}
            className="text-xs text-zinc-400 hover:text-zinc-200 px-2">&#x270E;</button>
          <button onClick={() => remove.mutate(rule.id)}
            className="text-xs text-zinc-500 hover:text-red-400 px-2">&#x2715;</button>
        </div>
      ))}
    </div>
  )
}
