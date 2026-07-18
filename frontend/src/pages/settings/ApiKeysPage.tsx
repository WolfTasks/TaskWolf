import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from '@/hooks/useApiKeys'
import type { CreateApiKeyResponse } from '@/hooks/useApiKeys'
import { DataTable, type Column } from '@/components/table/DataTable'
import { useTranslation } from 'react-i18next'
import { formatDate } from '@/i18n/format'

export function ApiKeysPage() {
  const { t } = useTranslation('settings')
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
      alert(e.response?.data?.message || t('apiKeys.createFailed'))
    }
  }

  function handleCopy() {
    if (newKey) {
      navigator.clipboard.writeText(newKey.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const columns: Column<(typeof keys)[number]>[] = [
    { key: 'prefix', header: t('shared.col.prefix'), width: '140px', cell: k => <code className="text-green-400">{k.keyPrefix}…</code> },
    { key: 'name', header: t('shared.col.name'), cell: k => k.name },
    { key: 'lastUsed', header: t('shared.col.lastUsed'), width: '120px', cell: k => <span className="text-gray-400">{k.lastUsedAt ? formatDate(k.lastUsedAt) : t('shared.never')}</span> },
    { key: 'expires', header: t('shared.col.expires'), width: '120px', cell: k => <span className="text-gray-400">{k.expiresAt ? formatDate(k.expiresAt) : t('shared.never')}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: k => (
        <button
          onClick={() => revokeKey.mutate(k.id, {
            onError: (e: any) => alert(e.response?.data?.message || t('apiKeys.revokeFailed')),
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
        <h1 className="text-2xl font-bold">{t('apiKeys.title')}</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          {t('apiKeys.create')}
        </button>
      </div>

      {newKey && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ {t('apiKeys.copyWarning')}
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newKey.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? t('shared.copied') : t('shared.copy')}
            </button>
          </div>
          <button
            onClick={() => setNewKey(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            {t('shared.dismiss')}
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700">
          <h2 className="text-sm font-semibold mb-3">{t('apiKeys.newTitle')}</h2>
          <input
            type="text"
            placeholder={t('apiKeys.namePlaceholder')}
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
              {createKey.isPending ? t('shared.creating') : t('shared.create')}
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
        rows={keys}
        rowKey={k => k.id}
        empty={t('apiKeys.empty')}
      />
    </div>
  )
}
