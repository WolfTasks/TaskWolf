import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { IssueDialog } from '@/components/issue/IssueDialog'

interface Props { projectKey: string }

export function IssueDialogHost({ projectKey }: Props) {
  const [searchParams, setSearchParams] = useSearchParams()
  const issueKey = searchParams.get('issue')

  const close = useCallback(() => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('issue')
      return next
    })
  }, [setSearchParams])

  if (!issueKey) return null
  return <IssueDialog projectKey={projectKey} issueKey={issueKey} onClose={close} />
}
