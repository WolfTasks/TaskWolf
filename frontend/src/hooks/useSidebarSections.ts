import { useCallback, useState } from 'react'

const STORAGE_KEY = 'sidebar-sections-collapsed'

type CollapsedMap = Record<string, boolean>

function readStored(): CollapsedMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as CollapsedMap) : {}
  } catch {
    return {}
  }
}

export function useSidebarSections(): {
  isOpen: (id: string) => boolean
  toggle: (id: string) => void
} {
  const [collapsed, setCollapsed] = useState<CollapsedMap>(readStored)

  const isOpen = useCallback((id: string) => collapsed[id] !== true, [collapsed])

  const toggle = useCallback((id: string) => {
    setCollapsed(prev => {
      const next = { ...readStored(), [id]: !prev[id] }
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      } catch {
        // ignore — storage may be unavailable (private mode / quota)
      }
      return next
    })
  }, [])

  return { isOpen, toggle }
}
