import { useTranslation } from 'react-i18next'
import { useNotifications, useMarkRead } from '@/hooks/useNotifications'
import { formatRelativeTime } from '@/i18n/format'

export function NotificationsPage() {
  const { t } = useTranslation('notifications')
  const { data, isLoading } = useNotifications()
  const markRead = useMarkRead()
  const notifications = data?.content ?? []

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-white mb-6">{t('title')}</h1>

      {isLoading && <div className="text-gray-500">{t('common:loading')}</div>}

      {!isLoading && notifications.length === 0 && (
        <p className="text-gray-500 italic">{t('empty')}</p>
      )}

      <div className="space-y-2">
        {notifications.map(n => (
          <div
            key={n.id}
            onClick={() => {
              if (!n.read) markRead.mutate(n.id)
              if (n.link) window.location.href = n.link
            }}
            className={`p-4 rounded-lg border cursor-pointer transition-colors ${
              n.read
                ? 'bg-gray-900 border-gray-800 text-gray-400'
                : 'bg-gray-800 border-gray-700 text-white hover:bg-gray-750'
            }`}
          >
            <div className="flex items-start justify-between gap-4">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-0.5">
                  <span className="text-xs text-gray-500">{t(`type.${n.type}`, { defaultValue: n.type })}</span>
                  {!n.read && (
                    <span className="w-2 h-2 rounded-full bg-indigo-500 flex-shrink-0" />
                  )}
                </div>
                <p className="text-sm font-medium truncate">{n.title}</p>
                {n.body && <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{n.body}</p>}
              </div>
              <span className="text-xs text-gray-600 flex-shrink-0">{formatRelativeTime(n.createdAt)}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
