import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { serviceDeskApi } from '@/api/servicedesk'

const SLA_COLOR = (status: string) =>
  status === 'BREACHED'
    ? 'bg-red-900/40 text-red-400'
    : status === 'WARNING'
    ? 'bg-yellow-900/40 text-yellow-400'
    : 'bg-green-900/40 text-green-400'

function computeSlaStatus(
  slaStartTime: string | null | undefined,
  slaPolicy: { durationMinutes: number } | null | undefined
): string {
  if (!slaStartTime || !slaPolicy) return 'N/A'
  const start = new Date(slaStartTime).getTime()
  const now = Date.now()
  const elapsedMinutes = (now - start) / 60_000
  const warningThreshold = slaPolicy.durationMinutes * 0.8
  if (elapsedMinutes >= slaPolicy.durationMinutes) return 'BREACHED'
  if (elapsedMinutes >= warningThreshold) return 'WARNING'
  return 'OK'
}

export default function ServiceDeskPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!

  const { data: tickets = [], isLoading: ticketsLoading } = useQuery({
    queryKey: ['tickets', projectKey],
    queryFn: () => serviceDeskApi.listTickets(projectKey),
  })

  const { data: slaPolicies = [] } = useQuery({
    queryKey: ['sla-policies', projectKey],
    queryFn: () => serviceDeskApi.listSlaPolicies(projectKey),
  })

  if (ticketsLoading) return <div className="p-6 text-gray-400">Loading...</div>

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-semibold">Service Desk</h1>

      {tickets.length === 0 ? (
        <p className="text-gray-500 text-sm">No tickets found.</p>
      ) : (
        <div className="space-y-2">
          {tickets.map((t: any) => {
            const matchedPolicy = slaPolicies.find(
              (p: any) => p.issueType === t.type || p.priority === t.priority
            ) ?? null
            const slaStatus = computeSlaStatus(t.slaStartTime, matchedPolicy)
            return (
              <div
                key={t.id}
                className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg p-3"
              >
                <span className="font-mono text-sm text-gray-400">{t.key}</span>
                <span className="flex-1 text-sm">{t.title}</span>
                <span className="text-xs text-gray-500">{t.status}</span>
                <span
                  className={`text-xs px-2 py-0.5 rounded font-medium ${SLA_COLOR(slaStatus)}`}
                >
                  {slaStatus}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
