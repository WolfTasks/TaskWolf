import { Outlet, NavLink, Link, useNavigate, useMatch } from 'react-router-dom'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { authApi } from '@/api/auth'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded text-sm ${isActive ? 'bg-gray-700 text-white font-semibold' : 'text-gray-300 hover:bg-gray-800 hover:text-white'}`

const subNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-1.5 rounded text-sm ${isActive ? 'bg-indigo-600 text-white font-semibold' : 'text-gray-400 hover:bg-gray-800 hover:text-white'}`

export function AppLayout() {
  const navigate = useNavigate()
  const insideProject = useMatch('/p/:key/*')
  const projectKey = insideProject?.params.key

  const logout = async () => {
    try { await authApi.logout() } catch { /* ignore */ }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white flex">
      <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col p-4">
        <Link to="/" className="text-xl font-bold mb-8">🐺 TaskWolf</Link>
        <nav className="flex flex-col gap-1 flex-1">
          <NavLink to="/" end className={navLinkClass}>Dashboard</NavLink>
          <NavLink to="/projects" end className={navLinkClass}>Projects</NavLink>

          {insideProject && projectKey && (
            <div className="mt-4">
              <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
                Project
              </p>
              <div className="flex flex-col gap-1">
                <NavLink to={`/p/${projectKey}/board`} className={subNavLinkClass}>Board</NavLink>
                <NavLink to={`/p/${projectKey}/backlog`} className={subNavLinkClass}>Backlog</NavLink>
                <NavLink to={`/p/${projectKey}/issues`} className={subNavLinkClass}>Issues</NavLink>
                <NavLink to={`/p/${projectKey}/reports`} className={subNavLinkClass}>Reports</NavLink>
                <NavLink to={`/p/${projectKey}/automation`} className={subNavLinkClass}>Automation</NavLink>
              </div>
            </div>
          )}
        </nav>
        <div className="flex items-center gap-2 mt-auto">
          <NotificationBell />
          <button onClick={logout} className="flex-1 px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
            Logout
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
