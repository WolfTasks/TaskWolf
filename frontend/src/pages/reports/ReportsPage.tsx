import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSprints } from '@/hooks/useSprints'
import { useBurndown, useVelocity } from '@/hooks/useReports'
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts'

export function ReportsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: sprints } = useSprints(key!)
  const closedSprints = sprints?.filter(s => s.status === 'CLOSED') ?? []
  const activeSprint = sprints?.find(s => s.status === 'ACTIVE')
  const defaultSprintId = closedSprints[closedSprints.length - 1]?.id ?? activeSprint?.id ?? null
  const [selectedSprintId, setSelectedSprintId] = useState<string | null>(null)
  const sprintId = selectedSprintId ?? defaultSprintId

  const { data: burndown } = useBurndown(key!, sprintId)
  const { data: velocity } = useVelocity(key!)

  const burndownData = burndown?.days.map(d => ({
    date: d.date.slice(5),
    Ideal: d.idealPoints,
    Actual: d.remainingPoints,
  })) ?? []

  const velocityData = velocity?.entries.map(e => ({
    name: e.sprintName,
    Planned: e.plannedPoints,
    Completed: e.completedPoints,
  })) ?? []

  const selectableSprints = sprints?.filter(s => s.status !== 'PLANNED') ?? []

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Reports</h1>
        {selectableSprints.length > 0 && (
          <select
            value={sprintId ?? ''}
            onChange={e => setSelectedSprintId(e.target.value)}
            className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5"
          >
            {selectableSprints.map(s => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        )}
      </div>

      <div className="mb-8">
        <h2 className="text-lg font-semibold text-white mb-4">Burndown Chart</h2>
        {burndownData.length === 0 ? (
          <p className="text-gray-500 text-sm">No burndown data available. Sprint must have a start date and story points.</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={burndownData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="date" stroke="#6b7280" tick={{ fontSize: 11 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 11 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Line type="monotone" dataKey="Ideal" stroke="#6b7280" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div>
        <h2 className="text-lg font-semibold text-white mb-4">Velocity</h2>
        {velocityData.length === 0 ? (
          <p className="text-gray-500 text-sm">No completed sprints yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={velocityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 11 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 11 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Bar dataKey="Planned" fill="#374151" radius={[3, 3, 0, 0]} />
              <Bar dataKey="Completed" fill="#3b82f6" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
