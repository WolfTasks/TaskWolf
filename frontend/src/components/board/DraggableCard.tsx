import { useRef } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { cn } from '@/lib/utils'
import { useOpenIssue } from '@/hooks/useOpenIssue'
import type { Issue } from '@/types'

const priorityColor: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props { issue: Issue }

export function DraggableCard({ issue }: Props) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: issue.id })
  const openIssue = useOpenIssue()
  const downPos = useRef<{ x: number; y: number } | null>(null)

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform) }}
      {...attributes}
      {...listeners}
      onPointerDownCapture={e => { downPos.current = { x: e.clientX, y: e.clientY } }}
      onClick={e => {
        const start = downPos.current
        downPos.current = null
        if (!start) return
        const moved = Math.hypot(e.clientX - start.x, e.clientY - start.y)
        if (moved < 5) openIssue(issue.key)
      }}
      className={cn(
        'bg-gray-900 border border-gray-800 rounded-lg p-3 cursor-grab active:cursor-grabbing select-none',
        isDragging && 'opacity-50 border-blue-500 z-50'
      )}
    >
      <div className="text-xs text-gray-500 font-mono mb-1">{issue.key}</div>
      <div className="text-sm text-white mb-2 line-clamp-2">{issue.title}</div>
      <div className="flex items-center gap-2">
        <span className={cn('text-xs font-medium', priorityColor[issue.priority] ?? 'text-gray-400')}>
          {issue.priority}
        </span>
        {issue.storyPoints != null && (
          <span className="ml-auto text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">
            {issue.storyPoints}
          </span>
        )}
      </div>
    </div>
  )
}
