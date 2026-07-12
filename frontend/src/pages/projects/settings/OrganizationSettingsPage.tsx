import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useProject } from '@/hooks/useProjects'
import { organizationsApi } from '@/api/organizations'
import { projectsApi } from '@/api/projects'

export function OrganizationSettingsPage() {
  const { key } = useParams<{ key: string }>()
  const qc = useQueryClient()
  const { data: project, isLoading } = useProject(key!)
  const [selected, setSelected] = useState('')
  const [error, setError] = useState('')

  const { data: myOrgs = [] } = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: () => organizationsApi.listMine().then(r => r.data),
  })

  const { data: currentOrg } = useQuery({
    queryKey: ['org', project?.orgId],
    queryFn: () => organizationsApi.getById(project!.orgId!).then(r => r.data),
    enabled: !!project?.orgId,
  })

  const setOrg = useMutation({
    mutationFn: (orgId: string | null) => projectsApi.setOrganization(key!, orgId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['projects', key] }) // useProject(key) — refreshes project.orgId + myRole
      qc.invalidateQueries({ queryKey: ['projects'] })       // project list
      setSelected(''); setError('')
    },
    onError: (e: unknown) => {
      const status = (e as { response?: { status?: number } }).response?.status
      setError(status === 403
        ? 'You must be an owner or admin of the target organization to assign this project to it.'
        : 'Could not update the organization.')
    },
  })

  if (isLoading || !project) return <div className="p-6 text-gray-400">Loading…</div>
  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don't have permission to manage this project's organization.</div>
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <h1 className="text-2xl font-semibold">Organization</h1>

      <div className="p-4 bg-gray-900 border border-gray-800 rounded-lg space-y-1">
        <div className="text-xs text-gray-400">Current organization</div>
        <div className="text-sm text-white">
          {project.orgId ? (currentOrg?.name ?? '…') : 'Not assigned'}
        </div>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      <div className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
        <label className="block text-xs text-gray-400">Assign to organization</label>
        <div className="flex items-center gap-2">
          <select
            value={selected}
            onChange={e => { setSelected(e.target.value); setError('') }}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white flex-1"
          >
            <option value="">Select an organization…</option>
            {myOrgs.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
          <button
            type="button"
            onClick={() => selected && setOrg.mutate(selected)}
            disabled={!selected || setOrg.isPending}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-1.5 rounded text-sm font-medium"
          >
            Assign
          </button>
        </div>
        {project.orgId && (
          <button
            type="button"
            onClick={() => setOrg.mutate(null)}
            disabled={setOrg.isPending}
            className="self-start text-xs text-red-400 hover:text-red-300 disabled:opacity-50"
          >
            Remove from organization
          </button>
        )}
      </div>
    </div>
  )
}
