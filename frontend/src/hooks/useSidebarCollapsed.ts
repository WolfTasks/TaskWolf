import { useEffect, useState, useCallback } from 'react'

const STORAGE_KEY = 'sidebar-collapsed'
const BREAKPOINT = '(max-width: 1024px)'

function readStoredPreference(): boolean {
  return localStorage.getItem(STORAGE_KEY) === 'true'
}

export function useSidebarCollapsed(): {
  collapsed: boolean
  toggle: () => void
  belowBreakpoint: boolean
} {
  const [preference, setPreference] = useState<boolean>(readStoredPreference)
  const [belowBreakpoint, setBelowBreakpoint] = useState<boolean>(
    () => window.matchMedia(BREAKPOINT).matches,
  )

  useEffect(() => {
    const mql = window.matchMedia(BREAKPOINT)
    const onChange = (e: MediaQueryListEvent) => setBelowBreakpoint(e.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [])

  const toggle = useCallback(() => {
    setPreference(prev => {
      const next = !prev
      localStorage.setItem(STORAGE_KEY, String(next))
      return next
    })
  }, [])

  return {
    collapsed: belowBreakpoint || preference,
    toggle,
    belowBreakpoint,
  }
}
