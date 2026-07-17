import { useCycleTimeAggregate, SprintCycleTime } from '@/hooks/useCycleTime'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { useTranslation } from 'react-i18next'

interface Props { projectKey: string }

export function CycleTimeWidget({ projectKey }: Props) {
  const { t } = useTranslation('dashboard')
  const { data } = useCycleTimeAggregate(projectKey)

  const chartData = data?.sprints
    .filter((s): s is SprintCycleTime & { averageCycleTimeHours: number } =>
      s.averageCycleTimeHours != null
    )
    .map(s => ({
      name: s.sprintName,
      Hours: Math.round(s.averageCycleTimeHours * 10) / 10,
    })) ?? []

  if (chartData.length === 0) return <p className="text-gray-500 text-xs">{t('widget.cycleTime.empty')}</p>

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 10 }} />
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} label={{ value: t('widget.cycleTime.axisHours'), angle: -90, position: 'insideLeft', fill: '#6b7280', fontSize: 10 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }}
          formatter={(v) => [`${v}h`, t('widget.cycleTime.tooltipLabel')]}
        />
        <Bar dataKey="Hours" fill="#8b5cf6" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
