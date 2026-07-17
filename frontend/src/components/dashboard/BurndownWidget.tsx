import { useState } from 'react'
import { useSprints } from '@/hooks/useSprints'
import { useBurndown } from '@/hooks/useReports'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { useTranslation } from 'react-i18next'

interface Props { projectKey: string; config: string | null }

export function BurndownWidget({ projectKey, config }: Props) {
  const { t } = useTranslation('dashboard')
  const { data: sprints } = useSprints(projectKey)
  let parsed: { sprintId?: string } = {}
  try {
    parsed = config ? JSON.parse(config) : {}
  } catch {
    // malformed config — use defaults
  }
  const defaultSprintId = parsed.sprintId ?? sprints?.find(s => s.status === 'ACTIVE')?.id ?? sprints?.filter(s => s.status === 'CLOSED').slice(-1)[0]?.id ?? null
  const [sprintId, setSprintId] = useState<string | null>(null)
  const activeId = sprintId ?? defaultSprintId
  // Hook is disabled when activeId is null (enabled: !!sprintId in useReports)
  const { data: burndown } = useBurndown(projectKey, activeId)

  const chartData = burndown?.days.map(d => ({
    date: d.date.slice(5),
    Ideal: d.idealPoints,
    Actual: d.remainingPoints,
  })) ?? []

  const selectable = sprints?.filter(s => s.status !== 'PLANNED') ?? []

  return (
    <div className="h-full flex flex-col gap-2">
      {selectable.length > 0 && (
        <select
          value={activeId ?? ''}
          onChange={e => setSprintId(e.target.value)}
          className="bg-gray-800 border border-gray-700 text-xs text-white rounded px-2 py-1 self-start"
        >
          {selectable.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      )}
      {chartData.length === 0
        ? <p className="text-gray-500 text-xs">{t('widget.burndown.empty')}</p>
        : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="date" stroke="#6b7280" tick={{ fontSize: 10 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Line type="monotone" dataKey="Ideal" name={t('widget.burndown.ideal')} stroke="#6b7280" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="Actual" name={t('widget.burndown.actual')} stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
    </div>
  )
}
