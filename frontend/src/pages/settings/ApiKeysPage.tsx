import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from '@/hooks/useApiKeys'
import type { CreateApiKeyResponse } from '@/hooks/useApiKeys'

export function ApiKeysPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: keys = [], isLoading } = useApiKeys(projectKey)
  const createKey = useCreateApiKey(projectKey)
  const revokeKey = useRevokeApiKey(projectKey)

  const [showCreate, setShowCreate] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [newKey, setNewKey] = useState<CreateApiKeyResponse | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleCreate() {
    if (!keyName.trim()) return
    try {
      const result = await createKey.mutateAsync({ name: keyName })
      setNewKey(result)
      setKeyName('')
      setShowCreate(false)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to create API key')
    }
  }

  function handleCopy() {
    if (newKey) {
      navigator.clipboard.writeText(newKey.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">API Keys</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          Create API Key
        </button>
      </div>

      {newKey && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ Copy your key now — it will not be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newKey.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button
            onClick={() => setNewKey(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700">
          <h2 className="text-sm font-semibold mb-3">New API Key</h2>
          <input
            type="text"
            placeholder="Key name (e.g. CI Pipeline)"
            value={keyName}
            onChange={e => setKeyName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3"
          />
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={createKey.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
            >
              {createKey.isPending ? 'Creating…' : 'Create'}
            </button>
            <button
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {keys.length === 0 ? (
        <p className="text-gray-400 text-sm">No API keys yet.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-400 border-b border-gray-700">
              <th className="pb-2">Prefix</th>
              <th className="pb-2">Name</th>
              <th className="pb-2">Last Used</th>
              <th className="pb-2">Expires</th>
              <th className="pb-2"></th>
            </tr>
          </thead>
          <tbody>
            {keys.map(k => (
              <tr key={k.id} className="border-b border-gray-800">
                <td className="py-3">
                  <code className="text-green-400">{k.keyPrefix}…</code>
                </td>
                <td className="py-3">{k.name}</td>
                <td className="py-3 text-gray-400">
                  {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-gray-400">
                  {k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-right">
                  <button
                    onClick={() => revokeKey.mutate(k.id, {
                      onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke key')
                    })}
                    className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
                  >
                    Revoke
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
