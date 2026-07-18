import { useState } from 'react'
import {
  useAccessTokens, useCreateAccessToken, useRevokeAccessToken,
} from '@/hooks/useAccessTokens'
import type { CreateAccessTokenResponse, TokenScope } from '@/hooks/useAccessTokens'
import { DataTable, type Column } from '@/components/table/DataTable'
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'

function expiryFromDays(days: string): string | null {
  if (!days) return null
  const d = new Date()
  d.setDate(d.getDate() + parseInt(days, 10))
  return d.toISOString()
}

export function AccessTokensPage() {
  const { t } = useTranslation('settings')
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
      alert(e.response?.data?.message || t('tokens.createFailed'))
    }
  }

  function handleCopy() {
    if (newToken) {
      navigator.clipboard.writeText(newToken.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const columns: Column<(typeof tokens)[number]>[] = [
    { key: 'prefix', header: t('shared.col.prefix'), width: '140px', cell: tok => <code className="text-green-400">{tok.tokenPrefix}…</code> },
    { key: 'name', header: t('shared.col.name'), cell: tok => tok.name },
    {
      key: 'scope',
      header: t('tokens.col.scope'),
      width: '140px',
      cell: tok => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          tok.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
        }`}>
          {tok.scope === 'READ_ONLY' ? t('tokens.scope.READ_ONLY') : t('tokens.scope.READ_WRITE')}
        </span>
      ),
    },
    { key: 'lastUsed', header: t('shared.col.lastUsed'), width: '120px', cell: tok => <span className="text-gray-400">{tok.lastUsedAt ? formatDate(tok.lastUsedAt) : t('shared.never')}</span> },
    { key: 'expires', header: t('shared.col.expires'), width: '120px', cell: tok => <span className="text-gray-400">{tok.expiresAt ? formatDate(tok.expiresAt) : t('shared.never')}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: tok => (
        <button
          onClick={() => revokeToken.mutate(tok.id, {
            onError: (e: any) => alert(e.response?.data?.message || t('tokens.revokeFailed')),
          })}
          className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
        >
          {t('shared.revoke')}
        </button>
      ),
    },
  ]

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  return (
    <div className="flex flex-col h-full min-h-0 max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('tokens.title')}</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          {t('tokens.create')}
        </button>
      </div>

      {newToken && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ {t('tokens.copyWarning')}
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newToken.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? t('shared.copied') : t('shared.copy')}
            </button>
          </div>
          <button
            onClick={() => setNewToken(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            {t('shared.dismiss')}
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700 flex flex-col gap-3">
          <h2 className="text-sm font-semibold">{t('tokens.newTitle')}</h2>
          <input
            type="text"
            placeholder={t('tokens.namePlaceholder')}
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
          />
          <label className="text-sm text-gray-300">
            {t('tokens.scopeLabel')}
            <select
              value={scope}
              onChange={e => setScope(e.target.value as TokenScope)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="READ_WRITE">{t('tokens.scope.READ_WRITE')}</option>
              <option value="READ_ONLY">{t('tokens.scope.READ_ONLY')}</option>
            </select>
          </label>
          <label className="text-sm text-gray-300">
            {t('tokens.expiresLabel')}
            <select
              value={expiryDays}
              onChange={e => setExpiryDays(e.target.value)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="">{t('shared.never')}</option>
              <option value="30">{t('shared.days', { count: 30 })}</option>
              <option value="60">{t('shared.days', { count: 60 })}</option>
              <option value="90">{t('shared.days', { count: 90 })}</option>
            </select>
          </label>
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={createToken.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
            >
              {createToken.isPending ? t('shared.creating') : t('shared.create')}
            </button>
            <button
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {t('common:cancel')}
            </button>
          </div>
        </div>
      )}

      <DataTable
        columns={columns}
        rows={tokens}
        rowKey={tok => tok.id}
        empty={t('tokens.empty')}
      />
    </div>
  )
}
