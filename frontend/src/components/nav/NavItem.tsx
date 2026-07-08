import { NavLink } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'

export interface NavItemProps {
  to: string
  label: string
  icon: LucideIcon
  collapsed: boolean
  end?: boolean
  variant?: 'top' | 'sub'
}

export function NavItem({ to, label, icon: Icon, collapsed, end, variant = 'top' }: NavItemProps) {
  const activeBg = variant === 'top' ? 'bg-gray-700 text-white font-semibold' : 'bg-indigo-600 text-white font-semibold'
  const idleText = variant === 'top' ? 'text-gray-300' : 'text-gray-400'
  const size = variant === 'top' ? 'py-2' : 'py-1.5'

  return (
    <NavLink
      to={to}
      end={end}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded text-sm ${size} ${collapsed ? 'justify-center px-2' : 'px-3'} ` +
        (isActive ? activeBg : `${idleText} hover:bg-gray-800 hover:text-white`)
      }
    >
      <Icon size={18} className="shrink-0" />
      {!collapsed && <span className="truncate">{label}</span>}
    </NavLink>
  )
}
