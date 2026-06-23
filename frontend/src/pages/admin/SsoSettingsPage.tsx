import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ssoApi, SsoConfigRequest } from '@/api/sso'

const emptyForm: SsoConfigRequest = {
  name: '',
  issuerUrl: '',
  clientId: '',
  clientSecret: '',
  enabled: true,
  autoProvision: true,
}

export function SsoSettingsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SsoConfigRequest>(emptyForm)
  const [formError, setFormError] = useState('')

  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['sso-configs'],
    queryFn: ssoApi.list,
  })

  const createMutation = useMutation({
    mutationFn: ssoApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sso-configs'] })
      setForm(emptyForm)
      setFormError('')
    },
    onError: () => setFormError('Failed to create SSO configuration.'),
  })

  const deleteMutation = useMutation({
    mutationFn: ssoApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sso-configs'] }),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    if (!form.name || !form.issuerUrl || !form.clientId || !form.clientSecret) {
      setFormError('All fields are required.')
      return
    }
    createMutation.mutate(form)
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-semibold">SSO Settings</h1>

      <section className="space-y-4">
        <h2 className="text-lg font-medium">Add SSO Provider</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          {formError && <p className="text-red-400 text-sm">{formError}</p>}
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Name</label>
            <input
              type="text"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Company Okta"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Issuer URL</label>
            <input
              type="url"
              value={form.issuerUrl}
              onChange={e => setForm(f => ({ ...f, issuerUrl: e.target.value }))}
              placeholder="https://accounts.example.com"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Client ID</label>
            <input
              type="text"
              value={form.clientId}
              onChange={e => setForm(f => ({ ...f, clientId: e.target.value }))}
              placeholder="client-id"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-400">Client Secret</label>
            <input
              type="password"
              value={form.clientSecret}
              onChange={e => setForm(f => ({ ...f, clientSecret: e.target.value }))}
              placeholder="client-secret"
              className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
            />
          </div>
          <div className="flex items-center gap-4">
            <label className="flex items-center gap-2 text-sm text-gray-300">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))}
              />
              Enabled
            </label>
            <label className="flex items-center gap-2 text-sm text-gray-300">
              <input
                type="checkbox"
                checked={form.autoProvision}
                onChange={e => setForm(f => ({ ...f, autoProvision: e.target.checked }))}
              />
              Auto-provision users
            </label>
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium"
          >
            {createMutation.isPending ? 'Adding...' : 'Add Provider'}
          </button>
        </form>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-medium">Configured Providers</h2>
        {isLoading && <p className="text-gray-400 text-sm">Loading...</p>}
        {!isLoading && configs.length === 0 && (
          <p className="text-gray-500 text-sm">No SSO providers configured.</p>
        )}
        {configs.map(config => (
          <div
            key={config.id}
            className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-center justify-between"
          >
            <div>
              <div className="font-medium text-sm">{config.name}</div>
              <div className="text-xs text-gray-400">{config.issuerUrl}</div>
              <div className="text-xs text-gray-500 mt-1">
                Client ID: {config.clientId} &bull;{' '}
                {config.enabled ? (
                  <span className="text-green-400">Enabled</span>
                ) : (
                  <span className="text-gray-500">Disabled</span>
                )}
                {config.autoProvision && (
                  <span className="ml-2 text-blue-400">Auto-provision</span>
                )}
              </div>
            </div>
            <button
              onClick={() => deleteMutation.mutate(config.id)}
              disabled={deleteMutation.isPending}
              className="text-red-400 hover:text-red-300 text-sm disabled:opacity-50"
            >
              Delete
            </button>
          </div>
        ))}
      </section>
    </div>
  )
}
