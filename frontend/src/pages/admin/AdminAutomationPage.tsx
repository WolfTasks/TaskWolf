import { useNavigate } from 'react-router-dom'
import { useSystemRules, useToggleSystemRule } from '../../hooks/useAutomation'

export function AdminAutomationPage() {
  const { data, isLoading } = useSystemRules()
  const toggle = useToggleSystemRule()
  const navigate = useNavigate()

  if (isLoading) return <div className="p-6 text-zinc-400">Lade systemweite Regeln...</div>

  return (
    <div className="p-6 max-w-3xl mx-auto flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-100">Systemweite Automation</h1>
        <button
          onClick={() => navigate('/admin/automation/new')}
          className="bg-indigo-600 text-white text-sm rounded-lg px-4 py-2 hover:bg-indigo-500"
        >
          + Neue Regel
        </button>
      </div>
      {data?.content.map(rule => (
        <div key={rule.id} className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex items-center gap-4">
          <div className="flex-1">
            <div className="font-medium text-zinc-100 text-sm">{rule.name}</div>
            <div className="text-xs text-zinc-400">{rule.triggerType.replace(/_/g, ' ')} · SYSTEM</div>
          </div>
          <button
            onClick={() => toggle.mutate(rule.id)}
            className={`text-xs rounded-full px-3 py-1 font-medium
              ${rule.enabled ? 'bg-emerald-900/50 text-emerald-300' : 'bg-zinc-800 text-zinc-500'}`}
          >
            {rule.enabled ? 'Aktiv' : 'Inaktiv'}
          </button>
        </div>
      ))}
    </div>
  )
}
