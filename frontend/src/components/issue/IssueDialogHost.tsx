import { useCallback } from 'react'
import { useSearchParams, useMatch } from 'react-router-dom'
import { IssueDialog } from '@/components/issue/IssueDialog'

interface Props { projectKey: string }

export function IssueDialogHost({ projectKey }: Props) {
  const [searchParams, setSearchParams] = useSearchParams()
  const issueKey = searchParams.get('issue')
  // When we are already on the issue's own full-page route, ignore ?issue= for
  // the *same* issue so the modal never stacks a second editor over the page.
  const fullPageMatch = useMatch('/p/:key/issues/:issueKey')

  const close = useCallback(() => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('issue')
      return next
    })
  }, [setSearchParams])

  if (!issueKey) return null
  if (fullPageMatch?.params.issueKey === issueKey) return null
  return <IssueDialog projectKey={projectKey} issueKey={issueKey} onClose={close} />
}
