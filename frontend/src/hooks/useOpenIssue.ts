import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

/** Öffnet ein Issue als Modal, indem `?issue=KEY` gesetzt wird (übrige Query-Parameter bleiben erhalten). */
export function useOpenIssue() {
  const [, setSearchParams] = useSearchParams()
  return useCallback(
    (issueKey: string) => {
      setSearchParams(prev => {
        const next = new URLSearchParams(prev)
        next.set('issue', issueKey)
        return next
      })
    },
    [setSearchParams],
  )
}
