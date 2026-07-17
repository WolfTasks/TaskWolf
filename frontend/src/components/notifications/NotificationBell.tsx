import { useNavigate } from 'react-router-dom'
import { useUnreadCount } from '@/hooks/useNotifications'
import { useTranslation } from 'react-i18next'

export function NotificationBell() {
  const { t } = useTranslation('notifications')
  const navigate = useNavigate()
  const count = useUnreadCount().data ?? 0

  return (
    <button
      onClick={() => navigate('/notifications')}
      className="relative p-1.5 text-gray-400 hover:text-white rounded"
      aria-label={t('bell.aria')}
    >
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round"
          d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
      </svg>
      {count > 0 && (
        <span className="absolute top-0 right-0 translate-x-1 -translate-y-1 min-w-[16px] h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center px-0.5">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </button>
  )
}
