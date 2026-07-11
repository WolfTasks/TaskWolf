import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useLabels, useCreateLabel, useUpdateLabel, useDeleteLabel } from '@/hooks/useLabels'
import { useProjectRole } from '@/hooks/useProjectRole'
import { LabelChip } from '@/components/issue/LabelChip'
import type { Label } from '@/types'

const PALETTE = [
  '#e11d48','#f97316','#eab308','#22c55e',
  '#14b8a6','#3b82f6','#8b5cf6','#ec4899',
  '#64748b','#0ea5e9','#84cc16','#f43f5e',
]

function LabelForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: { name: string; color: string }
  onSubmit: (name: string, color: string) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [color, setColor] = useState(initial?.color ?? PALETTE[0])
  const [error, setError] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) { setError('Name is required'); return }
    if (name.trim().length > 50) { setError('Max 50 characters'); return }
    onSubmit(name.trim(), color)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Name</label>
        <input
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          maxLength={50}
          placeholder="Label name"
          autoFocus
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Color</label>
        <div className="flex flex-wrap gap-2">
          {PALETTE.map(c => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              className={`w-6 h-6 rounded-full border-2 transition-transform ${color === c ? 'border-white scale-110' : 'border-transparent hover:scale-105'}`}
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Preview</label>
        <LabelChip label={{ id: '', name: name || 'Preview', color }} />
      </div>
      <div className="flex gap-2">
        <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">
          {initial ? 'Save' : 'Create'}
        </button>
        <button type="button" onClick={onCancel} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}

export function LabelsPage() {
  const { key } = useParams<{ key: string }>()
  const { canWrite } = useProjectRole(key!)
  const { data: labels = [], isLoading } = useLabels(key!)
  const createLabel = useCreateLabel(key!)
  const updateLabel = useUpdateLabel(key!)
  const deleteLabel = useDeleteLabel(key!)

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<Label | null>(null)
  const [apiError, setApiError] = useState('')

  async function handleCreate(name: string, color: string) {
    try {
      await createLabel.mutateAsync({ name, color })
      setShowCreate(false)
      setApiError('')
    } catch {
      setApiError('A label with that name already exists.')
    }
  }

  async function handleUpdate(name: string, color: string) {
    if (!editing) return
    try {
      await updateLabel.mutateAsync({ id: editing.id, name, color })
      setEditing(null)
      setApiError('')
    } catch {
      setApiError('A label with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this label? It will be removed from all issues.')) return
    await deleteLabel.mutateAsync(id)
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Labels</h1>
        {canWrite && !showCreate && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium"
          >
            + New Label
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <LabelForm onSubmit={handleCreate} onCancel={() => { setShowCreate(false); setApiError('') }} />
      )}

      <div className="flex flex-col gap-2">
        {labels.map(label => (
          <div key={label.id}>
            {editing?.id === label.id ? (
              <LabelForm
                initial={{ name: label.name, color: label.color }}
                onSubmit={handleUpdate}
                onCancel={() => { setEditing(null); setApiError('') }}
              />
            ) : (
              <div className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
                <LabelChip label={label} />
                <span className="text-xs text-gray-500 font-mono">{label.color}</span>
                {canWrite && (
                  <div className="ml-auto flex gap-2">
                    <button
                      onClick={() => setEditing(label)}
                      className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(label.id)}
                      className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700"
                    >
                      Delete
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        {labels.length === 0 && !showCreate && (
          <p className="text-sm text-gray-500 py-8 text-center">No labels yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
