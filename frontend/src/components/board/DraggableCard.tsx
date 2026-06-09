import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { cn } from '@/lib/utils'
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

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform) }}
      {...attributes}
      {...listeners}
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
