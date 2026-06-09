import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { DndContext, DragEndEvent, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import { useBoard, useMoveIssue } from '@/hooks/useBoard'
import { useCompleteSprint } from '@/hooks/useSprints'
import { useProjectSocket } from '@/hooks/useProjectSocket'
import { BoardColumn } from '@/components/board/BoardColumn'
import { SprintHeader } from '@/components/sprint/SprintHeader'
import { CompleteSprintDialog } from '@/components/sprint/CompleteSprintDialog'

export function BoardPage() {
  const { key } = useParams<{ key: string }>()
  useProjectSocket(key!)
  const { data: board, isLoading } = useBoard(key!)
  const moveIssue = useMoveIssue(key!)
  const completeSprint = useCompleteSprint(key!)
  const [showComplete, setShowComplete] = useState(false)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over) return
    const issueId = active.id as string
    const newStatusId = over.id as string
    const currentStatusId = board?.columns.find(c => c.issues.some(i => i.id === issueId))?.status.id
    if (currentStatusId !== newStatusId) {
      moveIssue.mutate({ issueId, newStatusId })
    }
  }

  const openIssueCount = board?.columns
    .filter(c => c.status.category !== 'DONE')
    .reduce((sum, c) => sum + c.issues.length, 0) ?? 0

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  if (!board) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <p className="text-gray-400 mb-4">No active sprint.</p>
        <Link to={`/p/${key}/backlog`} className="text-blue-400 hover:underline text-sm">
          Go to Backlog to start a sprint →
        </Link>
      </div>
    )
  }

  return (
    <div>
      <SprintHeader sprint={board.sprint} onComplete={() => setShowComplete(true)} />
      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="flex gap-4 overflow-x-auto pb-4">
          {board.columns.map(col => (
            <BoardColumn key={col.status.id} column={col} />
          ))}
        </div>
      </DndContext>
      {showComplete && (
        <CompleteSprintDialog
          sprintName={board.sprint.name}
          openIssueCount={openIssueCount}
          loading={completeSprint.isPending}
          onCancel={() => setShowComplete(false)}
          onConfirm={() => {
            completeSprint.mutate(board.sprint.id, {
              onSuccess: () => setShowComplete(false),
            })
          }}
        />
      )}
    </div>
  )
}
