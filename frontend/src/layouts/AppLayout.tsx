import { Outlet, NavLink, Link, useNavigate, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { OrgSwitcher } from '@/components/OrgSwitcher'
import { authApi } from '@/api/auth'
import { serviceDeskApi } from '@/api/servicedesk'
import { IssueDialogHost } from '@/components/issue/IssueDialogHost'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded text-sm ${isActive ? 'bg-gray-700 text-white font-semibold' : 'text-gray-300 hover:bg-gray-800 hover:text-white'}`

const subNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-1.5 rounded text-sm ${isActive ? 'bg-indigo-600 text-white font-semibold' : 'text-gray-400 hover:bg-gray-800 hover:text-white'}`

export function AppLayout() {
  const navigate = useNavigate()
  const insideProject = useMatch('/p/:key/*')
  const projectKey = insideProject?.params.key

  const { data: serviceDeskConfig } = useQuery({
    queryKey: ['service-desk-config', projectKey],
    queryFn: () => serviceDeskApi.get(projectKey!),
    enabled: !!projectKey,
  })

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
          <NavLink to="/orgs" end className={navLinkClass}>Organizations</NavLink>

          <div className="mt-4">
            <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Admin
            </p>
            <div className="flex flex-col gap-1">
              <NavLink to="/admin/audit" className={subNavLinkClass}>Audit Log</NavLink>
              <NavLink to="/admin/automation" className={subNavLinkClass}>Automation</NavLink>
            </div>
          </div>

          {insideProject && projectKey && (
            <div className="mt-4">
              <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
                Project
              </p>
              <div className="flex flex-col gap-1">
                <NavLink to={`/p/${projectKey}/dashboard`} className={subNavLinkClass}>Dashboard</NavLink>
                <NavLink to={`/p/${projectKey}/board`} className={subNavLinkClass}>Board</NavLink>
                <NavLink to={`/p/${projectKey}/backlog`} className={subNavLinkClass}>Backlog</NavLink>
                <NavLink to={`/p/${projectKey}/issues`} className={subNavLinkClass}>Issues</NavLink>
                <NavLink to={`/p/${projectKey}/reports`} className={subNavLinkClass}>Reports</NavLink>
                <NavLink to={`/p/${projectKey}/automation`} className={subNavLinkClass}>Automation</NavLink>
                {serviceDeskConfig?.enabled && (
                  <>
                    <NavLink to={`/p/${projectKey}/service-desk`} className={subNavLinkClass}>Service Desk</NavLink>
                    <NavLink to={`/p/${projectKey}/incidents`} className={subNavLinkClass}>Incidents</NavLink>
                  </>
                )}
              </div>

              <div className="mt-4">
                <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
                  Settings
                </p>
                <div className="flex flex-col gap-1">
                  <NavLink to={`/p/${projectKey}/settings/api-keys`} className={subNavLinkClass}>
                    API Keys
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/webhooks`} className={subNavLinkClass}>
                    Webhooks
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/integrations`} className={subNavLinkClass}>
                    Integrations
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/audit`} className={subNavLinkClass}>
                    Audit Log
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/labels`} className={subNavLinkClass}>
                    Labels
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/versions`} className={subNavLinkClass}>
                    Versions
                  </NavLink>
                  <NavLink to={`/p/${projectKey}/settings/custom-fields`} className={subNavLinkClass}>
                    Custom Fields
                  </NavLink>
                </div>
              </div>
            </div>
          )}
        </nav>
        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher />
          <div className="flex items-center gap-2">
            <NotificationBell />
            <button onClick={logout} className="flex-1 px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
              Logout
            </button>
          </div>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
    </div>
  )
}
