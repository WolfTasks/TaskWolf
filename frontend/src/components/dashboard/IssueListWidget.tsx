import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import { Link } from 'react-router-dom'
import type { Issue, Page } from '@/types'
import { useTranslation } from 'react-i18next'

interface Props {
  projectKey: string
  config: string | null
}

type FilterMode = 'MY_OPEN' | 'RECENTLY_UPDATED' | 'OVERDUE'

function buildParams(filter: FilterMode): Record<string, string | boolean | number> {
  switch (filter) {
    case 'MY_OPEN':
      return { assigneeMe: true, size: 20 }
    case 'RECENTLY_UPDATED':
      return { sort: 'updatedAt', size: 20 }
    case 'OVERDUE':
      return { overdue: true, size: 20 }
  }
}

export function IssueListWidget({ projectKey, config }: Props) {
  const { t } = useTranslation('dashboard')
  let parsed: { filter?: FilterMode } = {}
  try {
    parsed = config ? JSON.parse(config) : {}
  } catch {
    // malformed config — use defaults
  }
  const filter: FilterMode = parsed.filter ?? 'MY_OPEN'

  const { data, isLoading } = useQuery<Page<Issue>>({
    queryKey: ['issueList', projectKey, filter],
    queryFn: () =>
      apiClient
        .get<Page<Issue>>(`/projects/${projectKey}/issues`, { params: buildParams(filter) })
        .then(r => r.data),
  })

  const issues = data?.content ?? []

  return (
    <div className="flex flex-col gap-1 overflow-y-auto h-full">
      <p className="text-xs font-semibold text-gray-400 mb-1">{t(`palette.filters.${filter}`)}</p>
      {isLoading && <p className="text-gray-500 text-xs">{t('common:loading')}</p>}
      {!isLoading && issues.length === 0 && <p className="text-gray-500 text-xs">{t('widget.issueList.empty')}</p>}
      {issues.map(issue => (
        <Link
          key={issue.id}
          to={`/p/${projectKey}/issues/${issue.key}`}
          className="text-xs text-gray-300 hover:text-white hover:underline truncate"
        >
          <span className="text-gray-500 mr-1">{issue.key}</span>
          {issue.title}
        </Link>
      ))}
    </div>
  )
}
