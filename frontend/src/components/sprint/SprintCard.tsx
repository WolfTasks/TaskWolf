import type { Sprint } from '@/types'
import { cn } from '@/lib/utils'

interface Props {
  sprint: Sprint
  onClick: () => void
}

function formatRange(start: string | null, end: string | null): string | null {
  if (!start && !end) return null
  const fmt = (iso: string) => new Date(iso).toLocaleDateString()
  if (start && end) return `${fmt(start)} – ${fmt(end)}`
  return fmt((start ?? end)!)
}

const statusStyle: Record<Sprint['status'], string> = {
  ACTIVE: 'border-blue-500/60 bg-blue-500/5',
  PLANNED: 'border-gray-800 bg-gray-900/50',
  CLOSED: 'border-gray-800 bg-gray-900/30',
}

export function SprintCard({ sprint, onClick }: Props) {
  const range = formatRange(sprint.startDate, sprint.endDate)
  const planned = sprint.plannedPoints ?? 0
  const completed = sprint.completedPoints ?? 0
  const pct = planned > 0 ? Math.round((completed / planned) * 100) : 0

  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full text-left rounded-lg border p-4 hover:border-gray-600 transition-colors',
        statusStyle[sprint.status],
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-white truncate">{sprint.name}</h3>
          {sprint.goal && <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{sprint.goal}</p>}
        </div>
        <span className="text-xs text-gray-500 whitespace-nowrap shrink-0">
          {completed} / {planned} pts
        </span>
      </div>
      {range && <p className="text-xs text-gray-500 mt-2">{range}</p>}
      {sprint.status === 'ACTIVE' && planned > 0 && (
        <div className="mt-2 h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div className="h-full bg-blue-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      )}
    </button>
  )
}
