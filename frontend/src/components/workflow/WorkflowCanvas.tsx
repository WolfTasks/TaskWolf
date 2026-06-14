import { useState } from 'react'
import { DndContext, DragEndEvent } from '@dnd-kit/core'
import type { WorkflowEditorData, StatusPosition, WorkflowTransition, TransitionGuard } from '../../types'
import { StatusNode } from './StatusNode'
import { TransitionArrow } from './TransitionArrow'
import { TransitionGuardPanel } from './TransitionGuardPanel'

const NODE_W = 140
const NODE_H = 72

interface Props {
  data: WorkflowEditorData
  onSaveLayout: (positions: StatusPosition[]) => void
  onUpdateGuards: (tid: string, guards: TransitionGuard[]) => void
  onDeleteTransition: (tid: string) => void
}

export function WorkflowCanvas({ data, onSaveLayout, onUpdateGuards, onDeleteTransition }: Props) {
  const [positions, setPositions] = useState<Record<string, { x: number; y: number }>>(() => {
    const map: Record<string, { x: number; y: number }> = {}
    data.statuses.forEach((s, i) => {
      const found = data.layout.find(l => l.statusId === s.id)
      map[s.id] = found ? { x: found.x, y: found.y } : { x: 60 + i * 200, y: 80 }
    })
    return map
  })
  const [selectedTransition, setSelectedTransition] = useState<WorkflowTransition | null>(null)

  function handleDragEnd(e: DragEndEvent) {
    const id = e.active.id as string
    const prev = positions[id] ?? { x: 0, y: 0 }
    const next = { x: prev.x + (e.delta.x ?? 0), y: prev.y + (e.delta.y ?? 0) }
    const updated = { ...positions, [id]: next }
    setPositions(updated)
    onSaveLayout(Object.entries(updated).map(([statusId, p]) => ({ statusId, x: p.x, y: p.y })))
  }

  return (
    <div className="relative w-full h-[520px] bg-zinc-950 rounded-xl border border-zinc-800 overflow-hidden">
      <svg className="absolute inset-0 w-full h-full pointer-events-none">
        <defs>
          <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
            <path d="M0,0 L0,6 L9,3 z" fill="#6366f1" />
          </marker>
        </defs>
        {data.transitions.map(t => {
          const from = t.fromStatusId ? positions[t.fromStatusId] : null
          const to = positions[t.toStatusId]
          if (!to) return null
          const fx = from ? from.x + NODE_W / 2 : 0
          const fy = from ? from.y + NODE_H / 2 : 20
          const tx = to.x + NODE_W / 2
          const ty = to.y + NODE_H / 2
          return (
            <g key={t.id} style={{ pointerEvents: 'all' }}>
              <TransitionArrow
                x1={fx} y1={fy} x2={tx} y2={ty}
                hasGuards={!!t.guards && t.guards !== '[]'}
                onClick={() => setSelectedTransition(t)}
              />
            </g>
          )
        })}
      </svg>
      <DndContext onDragEnd={handleDragEnd}>
        {data.statuses.map(s => (
          <StatusNode
            key={s.id}
            status={s}
            x={positions[s.id]?.x ?? 0}
            y={positions[s.id]?.y ?? 0}
            selected={false}
            onClick={() => {}}
          />
        ))}
      </DndContext>
      {selectedTransition && (
        <TransitionGuardPanel
          transition={selectedTransition}
          onSave={guards => { onUpdateGuards(selectedTransition.id, guards); setSelectedTransition(null) }}
          onDelete={() => { onDeleteTransition(selectedTransition.id); setSelectedTransition(null) }}
          onClose={() => setSelectedTransition(null)}
        />
      )}
    </div>
  )
}
