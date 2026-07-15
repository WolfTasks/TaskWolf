import type { ActivityItem } from '@/types'
import { useTranslation } from 'react-i18next'
import { useActivity } from '@/hooks/useComments'
import { formatRelativeTime } from '@/i18n/format'

interface Props {
  projectKey: string
  issueKey: string
}

export function ActivityFeed({ projectKey, issueKey }: Props) {
  const { t } = useTranslation('comments')
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useActivity(projectKey, issueKey)

  const describe = (item: ActivityItem): string => {
    switch (item.type) {
      case 'COMMENT': return t('activity.COMMENT')
      case 'STATUS_CHANGED': return t('activity.STATUS_CHANGED', { from: item.oldValue, to: item.newValue })
      case 'ASSIGNED': return t('activity.ASSIGNED', { to: item.newValue })
      case 'UNASSIGNED': return t('activity.UNASSIGNED', { from: item.oldValue })
      case 'PRIORITY_CHANGED': return t('activity.PRIORITY_CHANGED', { from: item.oldValue, to: item.newValue })
      case 'TITLE_CHANGED': return t('activity.TITLE_CHANGED', { to: item.newValue })
      case 'DESCRIPTION_CHANGED': return t('activity.DESCRIPTION_CHANGED')
      case 'STORY_POINTS_CHANGED': return t('activity.STORY_POINTS_CHANGED', { from: item.oldValue ?? '—', to: item.newValue })
      case 'DUE_DATE_CHANGED': return t('activity.DUE_DATE_CHANGED', { from: item.oldValue ?? '—', to: item.newValue ?? '—' })
      case 'SPRINT_CHANGED': return t('activity.SPRINT_CHANGED', { to: item.newValue ?? t('activity.backlog') })
      case 'ATTACHMENT_ADDED': return t('activity.ATTACHMENT_ADDED', { to: item.newValue })
      case 'ATTACHMENT_REMOVED': return t('activity.ATTACHMENT_REMOVED', { from: item.oldValue })
      default: return (item.type as string).toLowerCase().replace(/_/g, ' ')
    }
  }

  // Pages arrive newest-first; keep that order for the activity log.
  const items: ActivityItem[] = (data?.pages ?? []).flatMap(p => p.content)

  if (isLoading) return <div className="text-gray-500 text-sm">{t('feed.loading')}</div>

  if (items.length === 0) {
    return <p className="text-gray-600 text-sm italic">{t('feed.empty')}</p>
  }

  return (
    <div className="max-h-[26rem] overflow-y-auto space-y-2 pr-1">
      {items.map((item: ActivityItem) => (
        <div key={item.id} className="flex gap-2 items-start text-sm">
          <div className="w-1.5 h-1.5 rounded-full bg-gray-600 mt-1.5 flex-shrink-0" />
          <div>
            <span className="text-gray-300">{describe(item)}</span>
            <span className="text-gray-600 ml-2 text-xs">
              {formatRelativeTime(item.createdAt)}
            </span>
          </div>
        </div>
      ))}

      {hasNextPage && (
        <button
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="text-xs text-indigo-400 hover:text-indigo-300 disabled:opacity-50"
        >
          {isFetchingNextPage ? t('common:loading') : t('feed.loadMore')}
        </button>
      )}
    </div>
  )
}
