import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { IssueDetailContent } from '@/components/issue/IssueDetailContent'

interface Props {
  projectKey: string
  issueKey: string
  onClose: () => void
}

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

function getFocusable(panel: HTMLElement): HTMLElement[] {
  return Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    el => el.offsetParent !== null || el === document.activeElement
  )
}

export function IssueDialog({ projectKey, issueKey, onClose }: Props) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null
    const previousBodyOverflow = document.body.style.overflow
    const appRoot = document.getElementById('app-root')

    document.body.style.overflow = 'hidden'
    appRoot?.setAttribute('aria-hidden', 'true')

    // Move focus into the dialog: prefer the first focusable descendant so
    // the panel container itself is never the active element (which would
    // make Node.contains() checks below indistinguishable from "escaped").
    const panel = panelRef.current
    if (panel) {
      const initialFocusable = getFocusable(panel)
      if (initialFocusable.length > 0) {
        initialFocusable[0].focus()
      } else {
        panel.focus()
      }
    }

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
        return
      }
      if (e.key === 'Tab') {
        const panel = panelRef.current
        if (!panel) return
        const focusable = getFocusable(panel)
        if (focusable.length === 0) {
          e.preventDefault()
          return
        }
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        const active = document.activeElement as HTMLElement | null
        // Treat the panel container itself as "escaped" too: Node.contains()
        // returns true for the node itself, so without this, focus sitting
        // on the container (e.g. right after open) would satisfy neither
        // wrap condition and let native Tab navigation escape the trap.
        const escaped = active === panel || !panel.contains(active)

        if (e.shiftKey) {
          if (active === first || escaped) {
            e.preventDefault()
            last.focus()
          }
        } else {
          if (active === last || escaped) {
            e.preventDefault()
            first.focus()
          }
        }
      }
    }
    window.addEventListener('keydown', onKey)

    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousBodyOverflow
      if (appRoot) appRoot.removeAttribute('aria-hidden')
      previouslyFocused?.focus()
    }
  }, [onClose])

  return createPortal(
    <div
      className="fixed inset-0 bg-black/60 flex items-start justify-center z-50 p-4 overflow-y-auto"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div
        ref={panelRef}
        tabIndex={-1}
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
    </div>,
    document.body
  )
}
