import { useState, useEffect } from 'react'
import { useNotificationPreferences, useUpdateNotificationPreferences } from '@/hooks/useMe'
import type { NotificationPreferenceItem } from '@/api/me'

const TYPE_LABELS: Record<string, string> = {
  COMMENT_MENTION: 'Mentions',
  ISSUE_ASSIGNED: 'Issue assigned to me',
  AUTOMATION: 'Automation',
  SLA_BREACHED: 'SLA breached',
}
const EMAIL_SUPPORTED = new Set(['COMMENT_MENTION', 'ISSUE_ASSIGNED'])

export function NotificationSettingsPage() {
  const { data, isLoading } = useNotificationPreferences()
  const update = useUpdateNotificationPreferences()
  const [rows, setRows] = useState<NotificationPreferenceItem[]>([])
  const [saved, setSaved] = useState(false)

  useEffect(() => { if (data) setRows(data) }, [data])

  function toggle(type: string, channel: 'inApp' | 'email') {
    setRows(rows.map(r => (r.type === type ? { ...r, [channel]: !r[channel] } : r)))
  }

  async function handleSave() {
    try {
      await update.mutateAsync(rows)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to save preferences')
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Notifications</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-400 border-b border-gray-700">
            <th className="py-2">Type</th>
            <th className="py-2 w-24 text-center">In-app</th>
            <th className="py-2 w-24 text-center">Email</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => {
            const emailSupported = EMAIL_SUPPORTED.has(row.type)
            return (
              <tr key={row.type} className="border-b border-gray-800">
                <td className="py-3">{TYPE_LABELS[row.type] ?? row.type}</td>
                <td className="py-3 text-center">
                  <input type="checkbox" checked={row.inApp} onChange={() => toggle(row.type, 'inApp')} />
                </td>
                <td className="py-3 text-center">
                  <input
                    type="checkbox"
                    checked={row.email}
                    onChange={() => toggle(row.type, 'email')}
                    title={emailSupported ? undefined : 'No email is sent for this type yet — saved for the future.'}
                  />
                  {!emailSupported && <span className="ml-1 text-xs text-gray-500">*</span>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-gray-500">* No email is currently sent for these types; the preference is saved for future use.</p>
      <div className="flex items-center gap-3 mt-6">
        <button
          onClick={handleSave}
          disabled={update.isPending}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {update.isPending ? 'Saving…' : 'Save'}
        </button>
        {saved && <span className="text-sm text-green-400">Saved</span>}
      </div>
    </div>
  )
}
