import { useParams } from 'react-router-dom'
import { useIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)
  const { data: me } = useMe()

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  return (
    <div className="max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <span className="text-xs px-2 py-0.5 bg-gray-800 rounded text-gray-400">{issue.type}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>
      <h1 className="text-2xl font-bold text-white mb-6">{issue.title}</h1>

      {/* Two-column layout */}
      <div className="grid grid-cols-3 gap-8">
        {/* Left: description + comments + activity */}
        <div className="col-span-2 space-y-8">
          {/* Description */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24">
              {issue.description ?? <span className="text-gray-600 italic">No description</span>}
            </div>
          </section>

          {/* Comments */}
          <section>
            <CommentThread
              projectKey={key!}
              issueKey={issueKey!}
              currentUserId={me?.id}
            />
          </section>

          {/* Activity */}
          <section>
            <ActivityFeed projectKey={key!} issueKey={issueKey!} />
          </section>

          {/* References */}
          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
                References
              </h3>
              <div className="space-y-2">
                {issue.refs.map((ref) => (
                  <a
                    key={ref.id}
                    href={ref.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors"
                  >
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">
                      {ref.provider}
                    </span>
                    <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">
                      {ref.refType}
                    </span>
                    <span className="text-sm text-blue-400 truncate">
                      {ref.title || ref.externalId}
                    </span>
                    <span className="text-xs text-gray-500 shrink-0">
                      {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
                    </span>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: metadata + attachments */}
        <div className="flex flex-col gap-6">
          {/* Metadata */}
          <section className="space-y-4">
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

            {issue.assigneeId && (
              <div>
                <label className="text-xs text-gray-500 uppercase mb-1 block">Assignee</label>
                <span className="text-sm text-gray-300 font-mono">{issue.assigneeId.slice(0, 8)}…</span>
              </div>
            )}
          </section>

          {/* Attachments */}
          <section>
            <AttachmentPanel
              projectKey={key!}
              issueKey={issueKey!}
              currentUserId={me?.id}
            />
          </section>
        </div>
      </div>
    </div>
  )
}
