import { useState } from 'react'
import { useCreateSprint } from '@/hooks/useSprints'

interface Props {
  projectKey: string
  onCreated: () => void
  onCancel: () => void
}

export function CreateSprintForm({ projectKey, onCreated, onCancel }: Props) {
  const createSprint = useCreateSprint(projectKey)
  const [name, setName] = useState(`Sprint ${Date.now()}`.slice(0, 20))
  const [goal, setGoal] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    await createSprint.mutateAsync({ name, goal: goal || undefined })
    onCreated()
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex gap-3 items-end">
      <div className="flex-1">
        <label className="text-xs text-gray-400 block mb-1">Sprint Name</label>
        <input
          value={name} onChange={e => setName(e.target.value)} required
          className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-white"
        />
      </div>
      <div className="flex-1">
        <label className="text-xs text-gray-400 block mb-1">Goal (optional)</label>
        <input
          value={goal} onChange={e => setGoal(e.target.value)}
          className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-white"
        />
      </div>
      <button type="submit" disabled={createSprint.isPending}
        className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm px-4 py-1.5 rounded">
        Create
      </button>
      <button type="button" onClick={onCancel} className="text-gray-400 hover:text-white text-sm px-3 py-1.5">
        Cancel
      </button>
    </form>
  )
}
