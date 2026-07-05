import { useParams } from 'react-router-dom'
import { IssueDetailContent } from '@/components/issue/IssueDetailContent'

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  return (
    <div className="max-w-5xl">
      <IssueDetailContent projectKey={key!} issueKey={issueKey!} />
    </div>
  )
}
