import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { IssueDetailContent } from '@/components/issue/IssueDetailContent'

interface Props {
  projectKey: string
  issueKey: string
  onClose: () => void
}

export function IssueDialog({ projectKey, issueKey, onClose }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-start justify-center z-50 p-4 overflow-y-auto"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div
        className="bg-gray-950 border border-gray-800 rounded-xl w-full max-w-5xl my-8 p-6 relative"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-end gap-3 mb-2">
          <Link
            to={`/p/${projectKey}/issues/${issueKey}`}
            className="text-xs text-gray-400 hover:text-white"
          >
            ⤢ Full view
          </Link>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-white text-lg leading-none"
          >
            ✕
          </button>
        </div>
        <IssueDetailContent projectKey={projectKey} issueKey={issueKey} />
      </div>
    </div>
  )
}
