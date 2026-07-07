import { useState } from 'react'
import {
  useAccessTokens, useCreateAccessToken, useRevokeAccessToken,
} from '@/hooks/useAccessTokens'
import type { CreateAccessTokenResponse, TokenScope } from '@/hooks/useAccessTokens'

function expiryFromDays(days: string): string | null {
  if (!days) return null
  const d = new Date()
  d.setDate(d.getDate() + parseInt(days, 10))
  return d.toISOString()
}

export function AccessTokensPage() {
  const { data: tokens = [], isLoading } = useAccessTokens()
  const createToken = useCreateAccessToken()
  const revokeToken = useRevokeAccessToken()

  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [scope, setScope] = useState<TokenScope>('READ_WRITE')
  const [expiryDays, setExpiryDays] = useState('')
  const [newToken, setNewToken] = useState<CreateAccessTokenResponse | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleCreate() {
    if (!name.trim()) return
    try {
      const result = await createToken.mutateAsync({
        name, scope, expiresAt: expiryFromDays(expiryDays),
      })
      setNewToken(result)
      setName(''); setScope('READ_WRITE'); setExpiryDays('')
      setShowCreate(false)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to create token')
    }
  }

  function handleCopy() {
    if (newToken) {
      navigator.clipboard.writeText(newToken.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Personal Access Tokens</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          Create Token
        </button>
      </div>

      {newToken && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ Copy your token now — it will not be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newToken.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button
            onClick={() => setNewToken(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700 flex flex-col gap-3">
          <h2 className="text-sm font-semibold">New Token</h2>
          <input
            type="text"
            placeholder="Token name (e.g. My CLI)"
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
          />
          <label className="text-sm text-gray-300">
            Scope
            <select
              value={scope}
              onChange={e => setScope(e.target.value as TokenScope)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="READ_WRITE">Read &amp; Write</option>
              <option value="READ_ONLY">Read-only</option>
            </select>
          </label>
          <label className="text-sm text-gray-300">
            Expires
            <select
              value={expiryDays}
              onChange={e => setExpiryDays(e.target.value)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="">Never</option>
              <option value="30">30 days</option>
              <option value="60">60 days</option>
              <option value="90">90 days</option>
            </select>
          </label>
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={createToken.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
            >
              {createToken.isPending ? 'Creating…' : 'Create'}
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

      {tokens.length === 0 ? (
        <p className="text-gray-400 text-sm">No tokens yet.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-400 border-b border-gray-700">
              <th className="pb-2">Prefix</th>
              <th className="pb-2">Name</th>
              <th className="pb-2">Scope</th>
              <th className="pb-2">Last Used</th>
              <th className="pb-2">Expires</th>
              <th className="pb-2"></th>
            </tr>
          </thead>
          <tbody>
            {tokens.map(t => (
              <tr key={t.id} className="border-b border-gray-800">
                <td className="py-3"><code className="text-green-400">{t.tokenPrefix}…</code></td>
                <td className="py-3">{t.name}</td>
                <td className="py-3">
                  <span className={`px-2 py-0.5 rounded text-xs ${
                    t.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
                  }`}>
                    {t.scope === 'READ_ONLY' ? 'Read-only' : 'Read & Write'}
                  </span>
                </td>
                <td className="py-3 text-gray-400">
                  {t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-gray-400">
                  {t.expiresAt ? new Date(t.expiresAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-right">
                  <button
                    onClick={() => revokeToken.mutate(t.id, {
                      onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke token'),
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
