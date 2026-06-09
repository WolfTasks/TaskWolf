import { useParams } from 'react-router-dom'
import { useIssue } from '@/hooks/useIssues'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  return (
    <div className="max-w-3xl">
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <span className="text-xs px-2 py-0.5 bg-gray-800 rounded text-gray-400">{issue.type}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>
      <h1 className="text-2xl font-bold text-white mb-6">{issue.title}</h1>

      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2">
          <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
          <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-32">
            {issue.description ?? <span className="text-gray-600 italic">No description</span>}
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs text-gray-500 uppercase mb-1 block">Priority</label>
            <span className={`text-sm font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </div>
          {issue.storyPoints != null && (
            <div>
              <label className="text-xs text-gray-500 uppercase mb-1 block">Story Points</label>
              <span className="text-sm text-white">{issue.storyPoints}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
