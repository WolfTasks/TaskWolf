import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../api/audit'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

export default function AuditLogPage() {
  const [action, setAction] = useState('')
  const [level, setLevel] = useState('')
  const { data } = useQuery({
    queryKey: ['audit', action, level],
    queryFn: () =>
      auditApi.listAll({
        ...(action && { action }),
        ...(level && { level }),
      }),
  })

  const handleExport = async (format: 'csv' | 'json') => {
    const res = await auditApi.exportAudit(format)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit.${format}`
    a.click()
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Audit Log</h1>
        <div className="flex gap-2">
          <button
            onClick={() => handleExport('csv')}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm font-medium"
          >
            Export CSV
          </button>
          <button
            onClick={() => handleExport('json')}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm font-medium"
          >
            Export JSON
          </button>
        </div>
      </div>
      <div className="flex gap-2">
        <input
          className="border rounded px-2 py-1 text-sm"
          placeholder="Filter action…"
          value={action}
          onChange={e => setAction(e.target.value)}
        />
        <select
          className="border rounded px-2 py-1 text-sm"
          value={level}
          onChange={e => setLevel(e.target.value)}
        >
          <option value="">All levels</option>
          <option value="SECURITY">Security</option>
          <option value="WRITE">Write</option>
          <option value="ALL">All</option>
        </select>
      </div>
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2 pr-4">Time</th>
            <th className="py-2 pr-4">User</th>
            <th className="py-2 pr-4">Action</th>
            <th className="py-2 pr-4">Level</th>
            <th className="py-2">Resource</th>
          </tr>
        </thead>
        <tbody>
          {data?.content?.map((e: AuditEvent) => (
            <tr key={e.id} className="border-b hover:bg-gray-800/40">
              <td className="py-2 pr-4 text-gray-400">
                {new Date(e.timestamp).toLocaleString()}
              </td>
              <td className="py-2 pr-4">{e.userEmail}</td>
              <td className="py-2 pr-4 font-mono text-xs">{e.action}</td>
              <td className="py-2 pr-4">
                <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
                  {e.level}
                </span>
              </td>
              <td className="py-2 text-gray-400">
                {e.resourceType} {e.resourceId}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
