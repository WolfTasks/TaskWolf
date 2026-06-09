import { Outlet } from 'react-router-dom'

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="w-full max-w-md p-8">
        <h1 className="text-3xl font-bold text-white text-center mb-8">🐺 TaskWolf</h1>
        <Outlet />
      </div>
    </div>
  )
}
