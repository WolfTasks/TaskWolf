import type { BoardSprintSummary } from '@/types'

interface Props {
  sprint: BoardSprintSummary
  onComplete: () => void
}

export function SprintHeader({ sprint, onComplete }: Props) {
  const pct = sprint.totalPoints && sprint.totalPoints > 0
    ? Math.round((sprint.completedPoints / sprint.totalPoints) * 100)
    : 0

  return (
    <div className="mb-6">
      <div className="flex items-start justify-between mb-2">
        <div>
          <h1 className="text-xl font-bold text-white">{sprint.name}</h1>
          {sprint.goal && <p className="text-sm text-gray-400 mt-0.5">{sprint.goal}</p>}
        </div>
        <button
          onClick={onComplete}
          className="text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 px-3 py-1.5 rounded border border-gray-700"
        >
          Complete Sprint
        </button>
      </div>
      <div className="flex items-center gap-4 text-sm text-gray-500">
        {sprint.daysRemaining != null && (
          <span>{sprint.daysRemaining} day{sprint.daysRemaining !== 1 ? 's' : ''} remaining</span>
        )}
        {sprint.totalPoints != null && (
          <span>{sprint.completedPoints} / {sprint.totalPoints} pts</span>
        )}
      </div>
      {sprint.totalPoints != null && sprint.totalPoints > 0 && (
        <div className="mt-2 h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div className="h-full bg-blue-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  )
}
