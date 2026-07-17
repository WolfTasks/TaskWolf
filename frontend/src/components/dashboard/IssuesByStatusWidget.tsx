import { useIssues } from '@/hooks/useIssues'
import { useTranslation } from 'react-i18next'

interface Props {
  projectKey: string
}

const CATEGORY_COLOR: Record<string, string> = {
  TODO: 'text-gray-400',
  IN_PROGRESS: 'text-blue-400',
  DONE: 'text-green-400',
}

export function IssuesByStatusWidget({ projectKey }: Props) {
  const { t } = useTranslation('dashboard')
  const { data } = useIssues(projectKey)
  const issues = data?.content ?? []

  const counts = issues.reduce<Record<string, number>>((acc, issue) => {
    const cat: string = issue.statusCategory ?? 'OTHER'
    acc[cat] = (acc[cat] ?? 0) + 1
    return acc
  }, {})

  const total = data?.totalElements ?? 0
  const loaded = issues.length
  const truncated = total > loaded

  return (
    <div className="flex flex-col gap-4 items-center justify-center h-full">
      <div className="flex gap-4 items-center justify-center">
        {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map(cat => (
          <div key={cat} className="flex flex-col items-center gap-1">
            <span className={`text-3xl font-bold ${CATEGORY_COLOR[cat]}`}>{counts[cat] ?? 0}</span>
            <span className="text-xs text-gray-500">{t(`byStatus.${cat}`)}</span>
          </div>
        ))}
        {(counts['OTHER'] ?? 0) > 0 && (
          <div className="flex flex-col items-center gap-1">
            <span className="text-3xl font-bold text-gray-600">{counts['OTHER']}</span>
            <span className="text-xs text-gray-500">{t('byStatus.OTHER')}</span>
          </div>
        )}
      </div>
      {truncated && (
        <p className="text-xs text-gray-600 text-center mt-2">{t('byStatus.showing', { loaded, total })}</p>
      )}
    </div>
  )
}
