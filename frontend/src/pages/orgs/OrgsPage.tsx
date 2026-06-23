import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, CreateOrganizationRequest } from '@/api/organizations'

const emptyForm: CreateOrganizationRequest = { name: '', slug: '' }

export function OrgsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateOrganizationRequest>(emptyForm)
  const [formError, setFormError] = useState('')

  const { data: orgs = [], isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationsApi.listAll().then(r => r.data),
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateOrganizationRequest) =>
      organizationsApi.create(data).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      setForm(emptyForm)
      setFormError('')
    },
    onError: () => setFormError('Failed to create organization.'),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    if (!form.name || !form.slug) {
      setFormError('Name and slug are required.')
      return
    }
    createMutation.mutate(form)
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-semibold">Organizations</h1>

      <section className="space-y-4">
        <h2 className="text-lg font-medium">Create Organization</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          {formError && <p className="text-red-400 text-sm">{formError}</p>}
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Name</label>
            <input
              type="text"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Acme Corp"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Slug</label>
            <input
              type="text"
              value={form.slug}
              onChange={e => setForm(f => ({ ...f, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-') }))}
              placeholder="e.g. acme-corp"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium"
          >
            {createMutation.isPending ? 'Creating...' : 'Create Organization'}
          </button>
        </form>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-medium">All Organizations</h2>
        {isLoading && <p className="text-gray-400 text-sm">Loading...</p>}
        {!isLoading && orgs.length === 0 && (
          <p className="text-gray-500 text-sm">No organizations yet.</p>
        )}
        {orgs.map(org => (
          <div
            key={org.id}
            className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-center justify-between"
          >
            <div>
              <div className="font-medium text-sm">{org.name}</div>
              <div className="text-xs text-gray-400">{org.slug}</div>
            </div>
            <a
              href={`/orgs/${org.id}/settings`}
              className="text-blue-400 hover:text-blue-300 text-sm"
            >
              Settings
            </a>
          </div>
        ))}
      </section>
    </div>
  )
}
