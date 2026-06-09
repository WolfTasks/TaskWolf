import { useDroppable } from '@dnd-kit/core'
import { cn } from '@/lib/utils'
import { DraggableCard } from './DraggableCard'
import type { BoardColumn as BoardColumnType } from '@/types'

interface Props { column: BoardColumnType }

export function BoardColumn({ column }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: column.status.id })

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex flex-col min-w-56 w-64 rounded-lg p-3 transition-colors',
        isOver ? 'bg-gray-800' : 'bg-gray-900/50'
      )}
    >
      <div className="flex items-center gap-2 mb-3">
        <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: column.status.color }} />
        <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">{column.status.name}</span>
        <span className="ml-auto text-xs text-gray-600 bg-gray-800 px-1.5 py-0.5 rounded">
          {column.issues.length}
        </span>
      </div>
      <div className="flex flex-col gap-2 min-h-16">
        {column.issues.map(issue => (
          <DraggableCard key={issue.id} issue={issue} />
        ))}
      </div>
    </div>
  )
}
