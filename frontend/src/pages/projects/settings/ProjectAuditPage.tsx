import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../../api/audit'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

export default function ProjectAuditPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!

  const [action, setAction] = useState('')
  const { data } = useQuery({
    queryKey: ['project-audit', projectKey, action],
    queryFn: () =>
      auditApi.listForProject(projectKey, {
        ...(action && { action }),
      }),
  })

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Audit Log</h1>
      </div>
      <div className="flex gap-2">
        <input
          className="border rounded px-2 py-1 text-sm"
          placeholder="Filter action…"
          value={action}
          onChange={e => setAction(e.target.value)}
        />
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
