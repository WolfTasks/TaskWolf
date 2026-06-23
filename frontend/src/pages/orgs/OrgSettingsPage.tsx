import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, AddMemberRequest } from '@/api/organizations'

export function OrgSettingsPage() {
  const { orgId } = useParams<{ orgId: string }>()
  const queryClient = useQueryClient()
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<'OWNER' | 'ADMIN' | 'MEMBER'>('MEMBER')
  const [addError, setAddError] = useState('')

  const { data: org, isLoading: orgLoading } = useQuery({
    queryKey: ['org', orgId],
    queryFn: () => organizationsApi.getById(orgId!).then(r => r.data),
    enabled: !!orgId,
  })

  const { data: members = [], isLoading: membersLoading } = useQuery({
    queryKey: ['org-members', orgId],
    queryFn: () => organizationsApi.listMembers(orgId!).then(r => r.data),
    enabled: !!orgId,
  })

  const addMutation = useMutation({
    mutationFn: (data: AddMemberRequest) =>
      organizationsApi.addMember(orgId!, data).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['org-members', orgId] })
      setUserId('')
      setRole('MEMBER')
      setAddError('')
    },
    onError: () => setAddError('Failed to add member.'),
  })

  const removeMutation = useMutation({
    mutationFn: (uid: string) => organizationsApi.removeMember(orgId!, uid),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault()
    setAddError('')
    if (!userId.trim()) {
      setAddError('User ID is required.')
      return
    }
    addMutation.mutate({ userId, role })
  }

  if (orgLoading) return <div className="p-6 text-gray-400">Loading...</div>
  if (!org) return <div className="p-6 text-red-400">Organization not found.</div>

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-semibold">{org.name} — Settings</h1>
      <p className="text-gray-400 text-sm">Slug: {org.slug}</p>

      <section className="space-y-4">
        <h2 className="text-lg font-medium">Add Member</h2>
        <form onSubmit={handleAdd} className="space-y-3">
          {addError && <p className="text-red-400 text-sm">{addError}</p>}
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">User ID</label>
            <input
              type="text"
              value={userId}
              onChange={e => setUserId(e.target.value)}
              placeholder="UUID of the user"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Role</label>
            <select
              value={role}
              onChange={e => setRole(e.target.value as 'OWNER' | 'ADMIN' | 'MEMBER')}
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            >
              <option value="MEMBER">Member</option>
              <option value="ADMIN">Admin</option>
              <option value="OWNER">Owner</option>
            </select>
          </div>
          <button
            type="submit"
            disabled={addMutation.isPending}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium"
          >
            {addMutation.isPending ? 'Adding...' : 'Add Member'}
          </button>
        </form>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-medium">Members</h2>
        {membersLoading && <p className="text-gray-400 text-sm">Loading...</p>}
        {!membersLoading && members.length === 0 && (
          <p className="text-gray-500 text-sm">No members found.</p>
        )}
        {members.map(m => (
          <div
            key={m.userId}
            className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-center justify-between"
          >
            <div>
              <div className="font-medium text-sm font-mono">{m.userId}</div>
              <div className="text-xs text-gray-400">{m.role}</div>
            </div>
            <button
              onClick={() => removeMutation.mutate(m.userId)}
              disabled={removeMutation.isPending}
              className="text-red-400 hover:text-red-300 text-sm disabled:opacity-50"
            >
              Remove
            </button>
          </div>
        ))}
      </section>
    </div>
  )
}
