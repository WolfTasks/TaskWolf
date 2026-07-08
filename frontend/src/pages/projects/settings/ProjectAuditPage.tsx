import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../../api/audit'
import { DataTable, type Column } from '@/components/table/DataTable'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

const columns: Column<AuditEvent>[] = [
  {
    key: 'time',
    header: 'Time',
    width: '180px',
    cell: e => <span className="text-gray-400">{new Date(e.timestamp).toLocaleString()}</span>,
  },
  { key: 'user', header: 'User', cell: e => e.userEmail },
  {
    key: 'action',
    header: 'Action',
    cell: e => <span className="font-mono text-xs">{e.action}</span>,
  },
  {
    key: 'level',
    header: 'Level',
    width: '120px',
    cell: e => (
      <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
        {e.level}
      </span>
    ),
  },
  {
    key: 'resource',
    header: 'Resource',
    cell: e => <span className="text-gray-400">{e.resourceType} {e.resourceId}</span>,
  },
]

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
    <div className="flex flex-col h-full min-h-0 space-y-4">
      <h1 className="text-2xl font-semibold">Audit Log</h1>
      <div className="flex gap-2">
        <input
          className="border rounded px-2 py-1 text-sm"
          placeholder="Filter action…"
          value={action}
          onChange={e => setAction(e.target.value)}
        />
      </div>
      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={e => e.id}
        empty="No audit events"
      />
    </div>
  )
}
