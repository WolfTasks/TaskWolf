import { useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useProjectIntegrations, useCreateIntegration, useDeleteIntegration
} from '@/hooks/useProjectIntegrations'
import type { CreateIntegrationResponse } from '@/hooks/useProjectIntegrations'

const PROVIDERS = ['GITHUB', 'GITLAB'] as const
type Provider = typeof PROVIDERS[number]

export function IntegrationsPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: integrations = [], isLoading } = useProjectIntegrations(projectKey)
  const createIntegration = useCreateIntegration(projectKey)
  const deleteIntegration = useDeleteIntegration(projectKey)

  const [connectingProvider, setConnectingProvider] = useState<Provider | null>(null)
  const [repoUrl, setRepoUrl] = useState('')
  const [newIntegration, setNewIntegration] = useState<CreateIntegrationResponse | null>(null)
  const [copiedUrl, setCopiedUrl] = useState(false)
  const [copiedSecret, setCopiedSecret] = useState(false)

  async function handleConnect(provider: Provider) {
    try {
      const result = await createIntegration.mutateAsync({ provider, repoUrl: repoUrl || undefined })
      setNewIntegration(result)
      setConnectingProvider(null)
      setRepoUrl('')
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to connect integration')
    }
  }

  function copy(text: string, which: 'url' | 'secret') {
    navigator.clipboard.writeText(text)
    if (which === 'url') { setCopiedUrl(true); setTimeout(() => setCopiedUrl(false), 2000) }
    else { setCopiedSecret(true); setTimeout(() => setCopiedSecret(false), 2000) }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  const activeProviders = new Set(integrations.map(i => i.provider))

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Integrations</h1>

      {newIntegration && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-3">
            ⚠ Save these values now — the secret will not be shown again.
          </p>
          <div className="space-y-2">
            <div>
              <p className="text-xs text-gray-400 mb-1">Webhook URL (paste into {newIntegration.provider}):</p>
              <div className="flex gap-2">
                <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-xs text-blue-400 break-all">
                  {newIntegration.webhookUrl}
                </code>
                <button onClick={() => copy(newIntegration.webhookUrl, 'url')}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-xs shrink-0">
                  {copiedUrl ? 'Copied!' : 'Copy'}
                </button>
              </div>
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-1">Webhook Secret:</p>
              <div className="flex gap-2">
                <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-xs text-green-400 break-all">
                  {newIntegration.plaintextSecret}
                </code>
                <button onClick={() => copy(newIntegration.plaintextSecret, 'secret')}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-xs shrink-0">
                  {copiedSecret ? 'Copied!' : 'Copy'}
                </button>
              </div>
            </div>
          </div>
          <button onClick={() => setNewIntegration(null)} className="mt-3 text-xs text-gray-400 hover:text-white">
            Dismiss
          </button>
        </div>
      )}

      <div className="space-y-4">
        {PROVIDERS.map(provider => {
          const existing = integrations.find(i => i.provider === provider)
          const isActive = activeProviders.has(provider)
          const isConnecting = connectingProvider === provider

          return (
            <div key={provider} className="p-4 bg-gray-800 rounded border border-gray-700">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 bg-gray-700 rounded flex items-center justify-center text-sm font-bold">
                    {provider === 'GITHUB' ? 'GH' : 'GL'}
                  </div>
                  <div>
                    <p className="font-medium">{provider === 'GITHUB' ? 'GitHub' : 'GitLab'}</p>
                    {existing?.repoUrl && (
                      <p className="text-xs text-gray-400">{existing.repoUrl}</p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {isActive ? (
                    <>
                      <span className="text-xs text-green-400 font-medium">● Active</span>
                      <button
                        onClick={() => existing && deleteIntegration.mutate(existing.id)}
                        className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 rounded text-xs"
                      >
                        Remove
                      </button>
                    </>
                  ) : (
                    <button
                      onClick={() => setConnectingProvider(provider)}
                      className="px-3 py-1 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
                    >
                      Connect
                    </button>
                  )}
                </div>
              </div>

              {isConnecting && (
                <div className="mt-4 border-t border-gray-700 pt-4">
                  <p className="text-xs text-gray-400 mb-2">
                    Repository URL (optional, for display only):
                  </p>
                  <input
                    type="text"
                    placeholder="https://github.com/org/repo"
                    value={repoUrl}
                    onChange={e => setRepoUrl(e.target.value)}
                    className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3"
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleConnect(provider)}
                      disabled={createIntegration.isPending}
                      className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
                    >
                      {createIntegration.isPending ? 'Connecting…' : 'Generate Webhook URL'}
                    </button>
                    <button
                      onClick={() => { setConnectingProvider(null); setRepoUrl('') }}
                      className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
