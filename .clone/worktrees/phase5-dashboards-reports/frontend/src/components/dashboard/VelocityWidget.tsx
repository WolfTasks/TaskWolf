import { useVelocity } from '@/hooks/useReports'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

interface Props { projectKey: string }

export function VelocityWidget({ projectKey }: Props) {
  const { data: velocity } = useVelocity(projectKey)

  const chartData = velocity?.entries.map(e => ({
    name: e.sprintName,
    Planned: e.plannedPoints,
    Completed: e.completedPoints,
  })) ?? []

  if (chartData.length === 0) return <p className="text-gray-500 text-xs">No completed sprints yet.</p>

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 10 }} />
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} />
        <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
        <Legend />
        <Bar dataKey="Planned" fill="#374151" radius={[3, 3, 0, 0]} />
        <Bar dataKey="Completed" fill="#3b82f6" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}