import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentThread } from './CommentThread'
import { ActivityFeed } from './ActivityFeed'

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
  readOnly?: boolean
}

export function CommentsActivityTabs({ projectKey, issueKey, currentUserId, readOnly }: Props) {
  const { t } = useTranslation('comments')
  const [tab, setTab] = useState<'comments' | 'activity'>('comments')

  return (
    <div>
      <div className="flex gap-1 border-b border-gray-800 mb-4">
        {(['comments', 'activity'] as const).map(tabKey => (
          <button
            key={tabKey}
            onClick={() => setTab(tabKey)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === tabKey
                ? 'border-indigo-500 text-white'
                : 'border-transparent text-gray-400 hover:text-gray-200'
            }`}
          >
            {tabKey === 'comments' ? t('tabs.comments') : t('tabs.activity')}
          </button>
        ))}
      </div>

      {tab === 'comments' ? (
        <CommentThread projectKey={projectKey} issueKey={issueKey} currentUserId={currentUserId} readOnly={readOnly} />
      ) : (
        <ActivityFeed projectKey={projectKey} issueKey={issueKey} />
      )}
    </div>
  )
}
