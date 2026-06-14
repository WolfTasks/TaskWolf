import { useParams } from 'react-router-dom'
import { useWorkflowEditor, useCreateStatus, useCreateTransition, useUpdateGuards, useDeleteTransition, useSaveLayout } from '../../hooks/useWorkflowEditor'
import { WorkflowCanvas } from '../../components/workflow/WorkflowCanvas'
import { useState } from 'react'

export function WorkflowEditorPage() {
  const { key } = useParams<{ key: string }>()
  const { data, isLoading } = useWorkflowEditor(key!)
  const createStatus = useCreateStatus(key!)
  const createTransition = useCreateTransition(key!)
  const updateGuards = useUpdateGuards(key!)
  const deleteTransition = useDeleteTransition(key!)
  const saveLayout = useSaveLayout(key!)
  const [newStatusName, setNewStatusName] = useState('')

  if (isLoading || !data) return <div className="p-6 text-zinc-400">Loading workflow...</div>

  return (
    <div className="p-6 max-w-5xl mx-auto flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-100">Workflow Editor</h1>
        <div className="flex gap-2">
          <input
            value={newStatusName}
            onChange={e => setNewStatusName(e.target.value)}
            placeholder="New status name"
            className="bg-zinc-800 border border-zinc-700 text-zinc-200 text-sm rounded px-3 py-1.5"
          />
          <button
            onClick={() => { createStatus.mutate({ name: newStatusName, category: 'TODO', color: '#6c8fef' }); setNewStatusName('') }}
            disabled={!newStatusName.trim()}
            className="bg-indigo-600 text-white text-sm rounded px-3 py-1.5 hover:bg-indigo-500 disabled:opacity-40"
          >
            + Status
          </button>
        </div>
      </div>
      <WorkflowCanvas
        data={data}
        onSaveLayout={positions => saveLayout.mutate(positions)}
        onUpdateGuards={(tid, guards) => updateGuards.mutate({ tid, guards })}
        onDeleteTransition={tid => deleteTransition.mutate(tid)}
      />
      <div className="bg-zinc-900 rounded-lg p-4 border border-zinc-800">
        <p className="text-sm text-zinc-400 mb-3">Add Transition</p>
        <div className="flex gap-2 flex-wrap">
          {data.statuses.map(from => (
            data.statuses.filter(to => to.id !== from.id).map(to => (
              <button
                key={`${from.id}-${to.id}`}
                onClick={() => createTransition.mutate({ fromStatusId: from.id, toStatusId: to.id })}
                className="text-xs bg-zinc-800 text-zinc-300 rounded px-2 py-1 hover:bg-zinc-700 border border-zinc-700"
              >
                {from.name} → {to.name}
              </button>
            ))
          ))}
        </div>
      </div>
    </div>
  )
}
