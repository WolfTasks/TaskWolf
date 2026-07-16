import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, Link } from 'react-router-dom'
import { DndContext, DragEndEvent, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import { useBoard, useMoveIssue } from '@/hooks/useBoard'
import { useCompleteSprint } from '@/hooks/useSprints'
import { useProjectSocket } from '@/hooks/useProjectSocket'
import { useProjectRole } from '@/hooks/useProjectRole'
import { BoardColumn } from '@/components/board/BoardColumn'
import { SprintHeader } from '@/components/sprint/SprintHeader'
import { CompleteSprintDialog } from '@/components/sprint/CompleteSprintDialog'

export function BoardPage() {
  const { t } = useTranslation('board')
  const { key } = useParams<{ key: string }>()
  useProjectSocket(key!)
  const { data: board, isLoading } = useBoard(key!)
  const { canWrite } = useProjectRole(key!)
  const moveIssue = useMoveIssue(key!)
  const completeSprint = useCompleteSprint(key!)
  const [showComplete, setShowComplete] = useState(false)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    if (!canWrite) return
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

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  if (!board) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <p className="text-gray-400 mb-4">{t('empty.title')}</p>
        <Link to={`/p/${key}/backlog`} className="text-blue-400 hover:underline text-sm">
          {t('empty.cta')} →
        </Link>
      </div>
    )
  }

  return (
    <div>
      <SprintHeader sprint={board.sprint} onComplete={() => setShowComplete(true)} canWrite={canWrite} />
      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="flex gap-4 overflow-x-auto pb-4">
          {board.columns.map(col => (
            <BoardColumn key={col.status.id} column={col} canWrite={canWrite} />
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
            if (!canWrite) return
            completeSprint.mutate(board.sprint.id, {
              onSuccess: () => setShowComplete(false),
            })
          }}
        />
      )}
    </div>
  )
}
