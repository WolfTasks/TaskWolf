import { DndContext, closestCenter, DragEndEvent } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy, arrayMove } from '@dnd-kit/sortable'
import type { RuleAction } from '../../types'
import { ActionRow } from './ActionRow'

interface Props {
  actions: RuleAction[]
  onChange: (actions: RuleAction[]) => void
}

const newAction = (position: number): RuleAction => ({
  position, type: 'SET_PRIORITY', params: { priority: 'HIGH' }
})

export function ActionList({ actions, onChange }: Props) {
  function handleDragEnd(e: DragEndEvent) {
    const { active, over } = e
    if (!over || active.id === over.id) return
    const oldIdx = actions.findIndex(a => a.position === active.id)
    const newIdx = actions.findIndex(a => a.position === over.id)
    const reordered = arrayMove(actions, oldIdx, newIdx).map((a, i) => ({ ...a, position: i }))
    onChange(reordered)
  }

  return (
    <div className="flex flex-col gap-2">
      <DndContext collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={actions.map(a => a.position)} strategy={verticalListSortingStrategy}>
          {actions.map((a, i) => (
            <ActionRow
              key={a.position}
              action={a}
              onChange={na => onChange(actions.map((x, idx) => idx === i ? na : x))}
              onRemove={() => onChange(actions.filter((_, idx) => idx !== i).map((x, idx) => ({ ...x, position: idx })))}
            />
          ))}
        </SortableContext>
      </DndContext>
      <button
        onClick={() => onChange([...actions, newAction(actions.length)])}
        className="text-sm text-zinc-400 hover:text-zinc-200 border border-dashed border-zinc-700 rounded-lg py-2 hover:border-zinc-500 transition-colors"
      >
        + Action hinzufügen
      </button>
    </div>
  )
}
