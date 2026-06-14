import { useDraggable } from '@dnd-kit/core'
import type { WorkflowStatus } from '../../types'

interface Props {
  status: WorkflowStatus
  x: number
  y: number
  selected: boolean
  onClick: () => void
}

export function StatusNode({ status, x, y, selected, onClick }: Props) {
  const { attributes, listeners, setNodeRef, transform } = useDraggable({ id: status.id })
  const dx = transform?.x ?? 0
  const dy = transform?.y ?? 0

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      onClick={(e) => { e.stopPropagation(); onClick() }}
      style={{ position: 'absolute', left: x + dx, top: y + dy, cursor: 'grab' }}
      className={`select-none rounded-lg border-2 px-4 py-3 min-w-[120px] text-center shadow-md transition-colors
        ${selected ? 'border-indigo-400' : 'border-zinc-600'} bg-zinc-800 hover:border-indigo-500`}
    >
      <div className="text-xs text-zinc-400 mb-1">{status.category}</div>
      <div className="font-semibold text-sm text-zinc-100">{status.name}</div>
      <div className="mt-1 h-1.5 w-full rounded-full" style={{ backgroundColor: status.color }} />
    </div>
  )
}
