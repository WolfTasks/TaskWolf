import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { serviceDeskApi } from '@/api/servicedesk'

const SEVERITY_COLOR: Record<string, string> = {
  P1: 'bg-red-600 text-white',
  P2: 'bg-orange-500 text-white',
  P3: 'bg-yellow-400 text-black',
  P4: 'bg-gray-600 text-gray-200',
}

export default function IncidentDashboardPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const queryClient = useQueryClient()

  const [resolving, setResolving] = useState<string | null>(null)
  const [postmortem, setPostmortem] = useState('')

  const { data: incidents = [], isLoading } = useQuery({
    queryKey: ['incidents', projectKey],
    queryFn: () => serviceDeskApi.listIncidents(projectKey),
  })

  const resolveMutation = useMutation({
    mutationFn: ({ id }: { id: string }) =>
      serviceDeskApi.resolveIncident(projectKey, id, postmortem || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incidents', projectKey] })
      setResolving(null)
      setPostmortem('')
    },
  })

  if (isLoading) return <div className="p-6 text-gray-400">Loading...</div>

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-semibold">Incidents</h1>

      {incidents.length === 0 ? (
        <p className="text-gray-500 text-sm">No incidents found.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3">
          {incidents.map((inc: any) => (
            <div
              key={inc.id}
              className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-start gap-4"
            >
              <span
                className={`text-xs font-bold px-2 py-1 rounded shrink-0 ${SEVERITY_COLOR[inc.severity] ?? 'bg-gray-700 text-gray-200'}`}
              >
                {inc.severity}
              </span>
              <div className="flex-1 space-y-1">
                <p className="font-medium text-sm">Issue: {inc.issueId}</p>
                {inc.resolvedAt && (
                  <p className="text-xs text-gray-400">
                    Resolved: {new Date(inc.resolvedAt).toLocaleString()}
                  </p>
                )}
                {inc.postmortemIssueId && (
                  <p className="text-xs text-gray-500">
                    Postmortem issue: {inc.postmortemIssueId}
                  </p>
                )}
                {!inc.resolvedAt && resolving === inc.id && (
                  <div className="mt-2 space-y-2">
                    <textarea
                      className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-sm text-white"
                      rows={4}
                      placeholder="Postmortem notes (optional)..."
                      value={postmortem}
                      onChange={e => setPostmortem(e.target.value)}
                    />
                    <div className="flex gap-2">
                      <button
                        onClick={() => resolveMutation.mutate({ id: inc.id })}
                        disabled={resolveMutation.isPending}
                        className="bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white rounded px-3 py-1.5 text-sm font-medium"
                      >
                        {resolveMutation.isPending ? 'Resolving...' : 'Confirm Resolve'}
                      </button>
                      <button
                        onClick={() => { setResolving(null); setPostmortem('') }}
                        className="bg-gray-700 hover:bg-gray-600 text-white rounded px-3 py-1.5 text-sm"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
              {!inc.resolvedAt && resolving !== inc.id && (
                <button
                  onClick={() => setResolving(inc.id)}
                  className="shrink-0 border border-gray-600 hover:border-gray-400 text-gray-300 hover:text-white rounded px-3 py-1.5 text-sm"
                >
                  Resolve
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
