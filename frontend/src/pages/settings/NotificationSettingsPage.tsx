import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNotificationPreferences, useUpdateNotificationPreferences } from '@/hooks/useMe'
import type { NotificationPreferenceItem } from '@/api/me'

const TYPE_LABEL_KEYS: Record<string, string> = {
  COMMENT_MENTION: 'notifications.types.mention',
  ISSUE_ASSIGNED: 'notifications.types.issueAssigned',
  AUTOMATION: 'notifications.types.automation',
  SLA_BREACHED: 'notifications.types.slaBreached',
}
const EMAIL_SUPPORTED = new Set(['COMMENT_MENTION', 'ISSUE_ASSIGNED'])

export function NotificationSettingsPage() {
  const { t } = useTranslation('settings')
  const { t: tc } = useTranslation('common')
  const { data, isLoading } = useNotificationPreferences()
  const update = useUpdateNotificationPreferences()
  const [rows, setRows] = useState<NotificationPreferenceItem[]>([])
  const [saved, setSaved] = useState(false)
  const seeded = useRef(false)

  // Seed the matrix once from the first load; don't let a background refetch
  // (e.g. on window focus) clobber unsaved toggles.
  useEffect(() => {
    if (data && !seeded.current) {
      setRows(data)
      seeded.current = true
    }
  }, [data])

  function toggle(type: string, channel: 'inApp' | 'email') {
    setRows(rows.map(r => (r.type === type ? { ...r, [channel]: !r[channel] } : r)))
  }

  async function handleSave() {
    try {
      await update.mutateAsync(rows)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e: any) {
      alert(e.response?.data?.message || t('notifications.saveFailed'))
    }
  }

  if (isLoading) return <div className="text-gray-400">{tc('loading')}</div>

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">{t('notifications.title')}</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-400 border-b border-gray-700">
            <th className="py-2">{t('notifications.columnType')}</th>
            <th className="py-2 w-24 text-center">{t('notifications.columnInApp')}</th>
            <th className="py-2 w-24 text-center">{t('notifications.columnEmail')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => {
            const emailSupported = EMAIL_SUPPORTED.has(row.type)
            const labelKey = TYPE_LABEL_KEYS[row.type]
            return (
              <tr key={row.type} className="border-b border-gray-800">
                <td className="py-3">{labelKey ? t(labelKey) : row.type}</td>
                <td className="py-3 text-center">
                  <input type="checkbox" checked={row.inApp} onChange={() => toggle(row.type, 'inApp')} />
                </td>
                <td className="py-3 text-center">
                  <input
                    type="checkbox"
                    checked={row.email}
                    onChange={() => toggle(row.type, 'email')}
                    title={emailSupported ? undefined : t('notifications.emailNotSupportedTooltip')}
                  />
                  {!emailSupported && <span className="ml-1 text-xs text-gray-500">*</span>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-gray-500">{t('notifications.emailNotSupportedNote')}</p>
      <div className="flex items-center gap-3 mt-6">
        <button
          onClick={handleSave}
          disabled={update.isPending}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {update.isPending ? tc('saving') : tc('save')}
        </button>
        {saved && <span className="text-sm text-green-400">{tc('saved')}</span>}
      </div>
    </div>
  )
}
