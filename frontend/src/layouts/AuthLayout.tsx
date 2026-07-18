import { Outlet } from 'react-router-dom'
import { VersionTag } from '@/components/VersionTag'

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="w-full max-w-md p-8">
        {/* i18n-ignore: brand name, not translated */}
        <h1 className="text-3xl font-bold text-white text-center mb-1">🐺 TaskWolf</h1>
        <VersionTag className="block text-center mb-8" />
        <Outlet />
      </div>
    </div>
  )
}
