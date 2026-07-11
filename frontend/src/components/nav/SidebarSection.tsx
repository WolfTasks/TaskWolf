import { type ReactNode } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { useSidebarSections } from '@/hooks/useSidebarSections'

interface SidebarSectionProps {
  id: string
  label: string
  railMode: boolean
  children: ReactNode
}

export function SidebarSection({ id, label, railMode, children }: SidebarSectionProps) {
  const { isOpen, toggle } = useSidebarSections()

  // Icon-rail mode: no header, no chevron — items always visible (unchanged behavior).
  if (railMode) {
    return <div className="flex flex-col gap-1">{children}</div>
  }

  const open = isOpen(id)
  return (
    <div>
      <button
        type="button"
        onClick={() => toggle(id)}
        aria-expanded={open}
        className="w-full flex items-center gap-1 px-3 mb-1 text-xs font-semibold text-gray-500 uppercase tracking-wider hover:text-gray-300"
      >
        {open ? <ChevronDown size={12} className="shrink-0" /> : <ChevronRight size={12} className="shrink-0" />}
        <span className="truncate">{label}</span>
      </button>
      {open && <div className="flex flex-col gap-1">{children}</div>}
    </div>
  )
}
