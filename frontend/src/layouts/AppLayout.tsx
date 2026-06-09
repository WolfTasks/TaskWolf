import { Outlet, Link, useNavigate } from 'react-router-dom'

export function AppLayout() {
  const navigate = useNavigate()
  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white flex">
      <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col p-4">
        <Link to="/" className="text-xl font-bold mb-8">🐺 TaskWolf</Link>
        <nav className="flex flex-col gap-2 flex-1">
          <Link to="/" className="px-3 py-2 rounded hover:bg-gray-800 text-sm">Dashboard</Link>
          <Link to="/projects" className="px-3 py-2 rounded hover:bg-gray-800 text-sm">Projects</Link>
        </nav>
        <button onClick={logout} className="px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
          Logout
        </button>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
