import { useState } from 'react'
import { CommentThread } from './CommentThread'
import { ActivityFeed } from './ActivityFeed'

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
}

export function CommentsActivityTabs({ projectKey, issueKey, currentUserId }: Props) {
  const [tab, setTab] = useState<'comments' | 'activity'>('comments')

  return (
    <div>
      <div className="flex gap-1 border-b border-gray-800 mb-4">
        {(['comments', 'activity'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t
                ? 'border-indigo-500 text-white'
                : 'border-transparent text-gray-400 hover:text-gray-200'
            }`}
          >
            {t === 'comments' ? 'Comments' : 'Activity'}
          </button>
        ))}
      </div>

      {tab === 'comments' ? (
        <CommentThread projectKey={projectKey} issueKey={issueKey} currentUserId={currentUserId} />
      ) : (
        <ActivityFeed projectKey={projectKey} issueKey={issueKey} />
      )}
    </div>
  )
}
