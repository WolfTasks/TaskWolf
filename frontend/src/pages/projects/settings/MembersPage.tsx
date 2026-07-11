import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useProject } from '@/hooks/useProjects'
import {
  useProjectMembers,
  useAddMember,
  useUpdateMemberRole,
  useRemoveMember,
} from '@/hooks/useProjectMembers'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useMe } from '@/hooks/useAuth'
import type { ProjectRole, UserSearchResult } from '@/types'

const ROLE_LABELS: Record<ProjectRole, string> = {
  VIEWER: 'Read-only',
  MEMBER: 'Read & Write',
  ADMIN: 'Admin',
}
const ROLE_OPTIONS: ProjectRole[] = ['VIEWER', 'MEMBER', 'ADMIN']

function AddMemberForm({ projectKey }: { projectKey: string }) {
  const [input, setInput] = useState('')
  const [debounced, setDebounced] = useState('')
  const [selected, setSelected] = useState<UserSearchResult | null>(null)
  const [role, setRole] = useState<ProjectRole>('MEMBER')
  const [error, setError] = useState('')

  const addMember = useAddMember(projectKey)
  const { data: results = [] } = useUserSearch(debounced)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(input), 300)
    return () => clearTimeout(t)
  }, [input])

  const showDropdown = !selected && debounced.trim().length >= 2 && results.length > 0

  async function handleAdd() {
    if (!selected) return
    try {
      await addMember.mutateAsync({ userId: selected.id, role })
      setInput(''); setDebounced(''); setSelected(null); setRole('MEMBER'); setError('')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } }).response?.status
      setError(status === 409 ? 'This user is already a member.' : 'Could not add member.')
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div className="relative">
        <label className="block text-xs text-gray-400 mb-1">Add member</label>
        <input
          value={selected ? `${selected.displayName} (${selected.email})` : input}
          onChange={e => { setSelected(null); setInput(e.target.value); setError('') }}
          placeholder="Search by name or email…"
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {showDropdown && (
          <ul className="absolute z-10 mt-1 w-full bg-gray-800 border border-gray-600 rounded shadow-lg max-h-56 overflow-auto">
            {results.map(u => (
              <li key={u.id}>
                <button
                  type="button"
                  onClick={() => { setSelected(u); setInput(''); }}
                  className="w-full text-left px-3 py-2 text-sm text-white hover:bg-gray-700"
                >
                  <span className="font-medium">{u.displayName}</span>
                  <span className="text-gray-400 ml-2">{u.email}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex items-center gap-2">
        <select
          value={role}
          onChange={e => setRole(e.target.value as ProjectRole)}
          className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white"
        >
          {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
        <button
          type="button"
          onClick={handleAdd}
          disabled={!selected || addMember.isPending}
          className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-1.5 rounded text-sm font-medium"
        >
          Add
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  )
}

export function MembersPage() {
  const { key } = useParams<{ key: string }>()
  const { data: project } = useProject(key!)
  const { data: me } = useMe()
  const { data: members = [], isLoading } = useProjectMembers(key!)
  const updateRole = useUpdateMemberRole(key!)
  const removeMember = useRemoveMember(key!)

  if (isLoading || !project) return <div className="text-gray-400 p-6">Loading…</div>

  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don’t have permission to manage members for this project.</div>
  }

  async function handleRemove(userId: string, name: string) {
    if (!confirm(`Remove ${name} from this project?`)) return
    await removeMember.mutateAsync(userId)
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <h1 className="text-2xl font-semibold">Members</h1>

      <AddMemberForm projectKey={key!} />

      <div className="flex flex-col gap-2">
        {members.map(({ user, role }) => {
          const isOwner = user.id === project.ownerId
          const isSelf = user.id === me?.id
          return (
            <div key={user.id} className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
              <div className="min-w-0">
                <div className="text-sm text-white truncate">{user.displayName}</div>
                <div className="text-xs text-gray-500 truncate">{user.email}</div>
              </div>
              {isOwner && (
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">Owner</span>
              )}
              {isSelf && !isOwner && (
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">You</span>
              )}
              <div className="ml-auto flex items-center gap-2">
                <select
                  value={role}
                  disabled={isOwner || isSelf || updateRole.isPending}
                  onChange={e => updateRole.mutate({ userId: user.id, role: e.target.value as ProjectRole })}
                  className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
                >
                  {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                </select>
                <button
                  onClick={() => handleRemove(user.id, user.displayName)}
                  disabled={isOwner}
                  className="text-xs text-red-400 hover:text-red-300 disabled:opacity-30 disabled:hover:text-red-400 px-2 py-1 rounded hover:bg-gray-700"
                >
                  Remove
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
