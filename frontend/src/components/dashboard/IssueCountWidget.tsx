import { useIssues } from '@/hooks/useIssues'
import { useTranslation } from 'react-i18next'

interface Props {
  projectKey: string
}

export function IssueCountWidget({ projectKey }: Props) {
  const { data } = useIssues(projectKey)
  const total = data?.totalElements ?? 0
  const { t } = useTranslation('dashboard')

  return (
    <div className="flex flex-col items-center justify-center h-full gap-1">
      <span className="text-4xl font-bold text-white">{total}</span>
      <span className="text-xs text-gray-400">{t('widget.title.ISSUE_COUNT')}</span>
    </div>
  )
}
