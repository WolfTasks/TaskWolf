import { useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useWebhooks, useCreateWebhook, useDeleteWebhook,
  useWebhookDeliveries, useTestPing, ALL_WEBHOOK_EVENTS,
} from '@/hooks/useWebhooks'
import type { CreateWebhookResult } from '@/hooks/useWebhooks'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/i18n/format'

export function WebhooksPage() {
  const { t } = useTranslation('settings')
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: webhooks = [], isLoading } = useWebhooks(projectKey)
  const createWebhook = useCreateWebhook(projectKey)
  const deleteWebhook = useDeleteWebhook(projectKey)
  const testPing = useTestPing(projectKey)

  const [showCreate, setShowCreate] = useState(false)
  const [url, setUrl] = useState('')
  const [selectedEvents, setSelectedEvents] = useState<string[]>([])
  const [newSecret, setNewSecret] = useState<CreateWebhookResult | null>(null)
  const [selectedWebhookId, setSelectedWebhookId] = useState<string | null>(null)
  const [copiedSecret, setCopiedSecret] = useState(false)

  const { data: deliveries = [] } = useWebhookDeliveries(projectKey, selectedWebhookId)

  function toggleEvent(e: string) {
    setSelectedEvents(prev => prev.includes(e) ? prev.filter(x => x !== e) : [...prev, e])
  }

  async function handleCreate() {
    if (!url.trim() || selectedEvents.length === 0) return
    try {
      const result = await createWebhook.mutateAsync({ url, events: selectedEvents })
      setNewSecret(result)
      setUrl(''); setSelectedEvents([]); setShowCreate(false)
    } catch (e: any) {
      alert(e.response?.data?.message || t('webhooks.createFailed'))
    }
  }

  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('webhooks.title')}</h1>
        <button onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium">
          {t('webhooks.add')}
        </button>
      </div>

      {newSecret && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ {t('webhooks.copyWarning')}
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newSecret.plaintextSecret}
            </code>
            <button onClick={() => { navigator.clipboard.writeText(newSecret.plaintextSecret); setCopiedSecret(true); setTimeout(() => setCopiedSecret(false), 2000) }}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">
              {copiedSecret ? t('shared.copied') : t('shared.copy')}
            </button>
          </div>
          <button onClick={() => setNewSecret(null)} className="mt-2 text-xs text-gray-400 hover:text-white">{t('shared.dismiss')}</button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700">
          <h2 className="text-sm font-semibold mb-3">{t('webhooks.newTitle')}</h2>
          <input type="text" placeholder={t('webhooks.urlPlaceholder')}
            value={url} onChange={e => setUrl(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3" />
          <p className="text-xs text-gray-400 mb-2">{t('webhooks.eventsLabel')}</p>
          <div className="grid grid-cols-3 gap-1 mb-3">
            {ALL_WEBHOOK_EVENTS.map(ev => (
              <label key={ev} className="flex items-center gap-2 text-xs cursor-pointer">
                <input type="checkbox" checked={selectedEvents.includes(ev)} onChange={() => toggleEvent(ev)} />
                <span className="text-gray-300">{ev}</span>
              </label>
            ))}
          </div>
          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={createWebhook.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm">
              {createWebhook.isPending ? t('shared.creating') : t('shared.create')}
            </button>
            <button onClick={() => setShowCreate(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">{t('common:cancel')}</button>
          </div>
        </div>
      )}

      <div className="space-y-3">
        {webhooks.map(wh => (
          <div key={wh.id} className="p-4 bg-gray-800 rounded border border-gray-700">
            <div className="flex items-start justify-between">
              <div>
                <code className="text-sm text-blue-400">{wh.url}</code>
                <div className="flex flex-wrap gap-1 mt-2">
                  {wh.events.map(ev => (
                    <span key={ev} className="px-2 py-0.5 bg-gray-700 rounded text-xs text-gray-300">{ev}</span>
                  ))}
                </div>
              </div>
              <div className="flex gap-2 ml-4 shrink-0">
                <button onClick={() => testPing.mutate(wh.id)}
                  className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">{t('webhooks.ping')}</button>
                <button onClick={() => setSelectedWebhookId(selectedWebhookId === wh.id ? null : wh.id)}
                  className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">
                  {selectedWebhookId === wh.id ? t('webhooks.hideLog') : t('webhooks.log')}
                </button>
                <button onClick={() => deleteWebhook.mutate(wh.id)}
                  className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 rounded text-xs">{t('webhooks.delete')}</button>
              </div>
            </div>

            {selectedWebhookId === wh.id && (
              <div className="mt-3 border-t border-gray-700 pt-3">
                <p className="text-xs text-gray-400 mb-2">{t('webhooks.deliveryLog')}</p>
                {deliveries.length === 0 ? (
                  <p className="text-xs text-gray-500">{t('webhooks.noDeliveries')}</p>
                ) : (
                  <div className="space-y-1">
                    {deliveries.map(d => (
                      <div key={d.id} className="flex items-center gap-3 text-xs">
                        <span className={d.responseStatus && d.responseStatus < 300 ? 'text-green-400' : 'text-red-400'}>
                          {d.responseStatus ?? '—'}
                        </span>
                        <span className="text-gray-400">{d.eventType}</span>
                        <span className="text-gray-500">
                          {d.createdAt ? formatDateTime(d.createdAt) : ''}
                        </span>
                        <span className="text-gray-500">{t('webhooks.attempt', { count: d.attemptCount })}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        {webhooks.length === 0 && <p className="text-gray-400 text-sm">{t('webhooks.empty')}</p>}
      </div>
    </div>
  )
}
