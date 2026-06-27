import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useVersions, useCreateVersion, useUpdateVersion, useDeleteVersion } from '@/hooks/useVersions'
import type { Version } from '@/types'

function VersionForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: { name: string }
  onSubmit: (name: string) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [error, setError] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) { setError('Name is required'); return }
    if (name.trim().length > 50) { setError('Max 50 characters'); return }
    onSubmit(name.trim())
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Name</label>
        <input
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          maxLength={50}
          placeholder="e.g. v1.0.0"
          autoFocus
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
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

export function VersionsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: versions = [], isLoading } = useVersions(key!)
  const createVersion = useCreateVersion(key!)
  const updateVersion = useUpdateVersion(key!)
  const deleteVersion = useDeleteVersion(key!)

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<Version | null>(null)
  const [apiError, setApiError] = useState('')

  async function handleCreate(name: string) {
    try {
      await createVersion.mutateAsync({ name })
      setShowCreate(false)
      setApiError('')
    } catch {
      setApiError('A version with that name already exists.')
    }
  }

  async function handleUpdate(name: string) {
    if (!editing) return
    try {
      await updateVersion.mutateAsync({ id: editing.id, name })
      setEditing(null)
      setApiError('')
    } catch {
      setApiError('A version with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this version? It will be removed from all issues.')) return
    await deleteVersion.mutateAsync(id)
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Versions</h1>
        {!showCreate && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium"
          >
            + New Version
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <VersionForm onSubmit={handleCreate} onCancel={() => { setShowCreate(false); setApiError('') }} />
      )}

      <div className="flex flex-col gap-2">
        {versions.map(version => (
          <div key={version.id}>
            {editing?.id === version.id ? (
              <VersionForm
                initial={{ name: version.name }}
                onSubmit={handleUpdate}
                onCancel={() => { setEditing(null); setApiError('') }}
              />
            ) : (
              <div className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
                <span className="text-sm text-white font-mono">{version.name}</span>
                <div className="ml-auto flex gap-2">
                  <button
                    onClick={() => setEditing(version)}
                    className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(version.id)}
                    className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Delete
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
        {versions.length === 0 && !showCreate && (
          <p className="text-sm text-gray-500 py-8 text-center">No versions yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
