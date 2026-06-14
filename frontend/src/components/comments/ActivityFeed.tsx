import type { ActivityItem } from '@/types'
import { useActivity } from '@/hooks/useComments'

const formatTime = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function describeActivity(item: ActivityItem): string {
  switch (item.type) {
    case 'COMMENT': return 'commented'
    case 'STATUS_CHANGED': return `changed status from "${item.oldValue}" to "${item.newValue}"`
    case 'ASSIGNED': return `assigned to ${item.newValue}`
    case 'UNASSIGNED': return `unassigned ${item.oldValue}`
    case 'PRIORITY_CHANGED': return `changed priority from ${item.oldValue} to ${item.newValue}`
    case 'TITLE_CHANGED': return `renamed to "${item.newValue}"`
    case 'DESCRIPTION_CHANGED': return 'updated description'
    case 'STORY_POINTS_CHANGED': return `changed story points from ${item.oldValue ?? '—'} to ${item.newValue}`
    case 'DUE_DATE_CHANGED': return `changed due date from ${item.oldValue ?? '—'} to ${item.newValue ?? '—'}`
    case 'SPRINT_CHANGED': return `moved to sprint ${item.newValue ?? '(backlog)'}`
    case 'ATTACHMENT_ADDED': return `added attachment: ${item.newValue}`
    case 'ATTACHMENT_REMOVED': return `removed attachment: ${item.oldValue}`
    default: return (item.type as string).toLowerCase().replace(/_/g, ' ')
  }
}

interface Props {
  projectKey: string
  issueKey: string
}

export function ActivityFeed({ projectKey, issueKey }: Props) {
  const { data, isLoading } = useActivity(projectKey, issueKey)
  const items = data?.content ?? []

  if (isLoading) return <div className="text-gray-500 text-sm">Loading activity...</div>

  if (items.length === 0) {
    return <p className="text-gray-600 text-sm italic">No activity yet</p>
  }

  return (
    <div className="space-y-2">
      <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wide">Activity</h3>
      {items.map((item: ActivityItem) => (
        <div key={item.id} className="flex gap-2 items-start text-sm">
          <div className="w-1.5 h-1.5 rounded-full bg-gray-600 mt-1.5 flex-shrink-0" />
          <div>
            <span className="text-gray-300">{describeActivity(item)}</span>
            <span className="text-gray-600 ml-2 text-xs">
              {formatTime(item.createdAt)}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}
