import { Outlet, NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { User, Shield, Bell, KeyRound, UserX } from 'lucide-react'

const items = [
  { to: '/settings/profile', labelKey: 'profile.title', icon: User },
  { to: '/settings/security', labelKey: 'security.title', icon: Shield },
  { to: '/settings/notifications', labelKey: 'notifications.title', icon: Bell },
  { to: '/settings/tokens', labelKey: 'tokens.title', icon: KeyRound },
  { to: '/settings/account', labelKey: 'account.title', icon: UserX },
]

export function SettingsLayout() {
  const { t } = useTranslation('settings')
  return (
    <div className="flex gap-8 h-full min-h-0">
      <nav className="w-48 shrink-0 flex flex-col gap-1">
        <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">{t('nav:section.settings')}</h2>
        {items.map(({ to, labelKey, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2 rounded text-sm ${
                isActive ? 'bg-indigo-600 text-white font-semibold' : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              }`
            }
          >
            <Icon size={18} className="shrink-0" />
            <span className="truncate">{t(labelKey)}</span>
          </NavLink>
        ))}
      </nav>
      <div className="flex-1 min-h-0">
        <Outlet />
      </div>
    </div>
  )
}
