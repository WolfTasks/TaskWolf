import { Outlet, Link, useNavigate, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  LayoutDashboard, FolderKanban, Building2, ScrollText, Zap, Users,
  KeyRound, User, Kanban, ListChecks, CalendarRange, ListTodo, BarChart3,
  LifeBuoy, AlertTriangle, KeySquare, Webhook, Plug, Tags, Milestone,
  SlidersHorizontal, ChevronLeft, ChevronRight, LogOut,
} from 'lucide-react'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { OrgSwitcher } from '@/components/OrgSwitcher'
import { authApi } from '@/api/auth'
import { serviceDeskApi } from '@/api/servicedesk'
import { IssueDialogHost } from '@/components/issue/IssueDialogHost'
import { VersionTag } from '@/components/VersionTag'
import { useSidebarCollapsed } from '@/hooks/useSidebarCollapsed'
import { NavItem } from '@/components/nav/NavItem'

export function AppLayout() {
  const navigate = useNavigate()
  const insideProject = useMatch('/p/:key/*')
  const projectKey = insideProject?.params.key
  const { collapsed, toggle, belowBreakpoint } = useSidebarCollapsed()

  const { data: me } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me().then(r => r.data),
  })

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

  const sectionLabel = (text: string) =>
    !collapsed && (
      <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">{text}</p>
    )

  return (
    <div id="app-root" className="min-h-screen bg-gray-950 text-white flex">
      <aside className={`${collapsed ? 'w-16' : 'w-56'} bg-gray-900 border-r border-gray-800 flex flex-col p-4 transition-[width] duration-200`}>
        <div className={`flex items-center mb-8 ${collapsed ? 'justify-center' : 'justify-between'}`}>
          {!collapsed && <Link to="/" className="text-xl font-bold">🐺 TaskWolf</Link>}
          {!belowBreakpoint && (
            <button
              onClick={toggle}
              title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              className="p-1 rounded text-gray-400 hover:bg-gray-800 hover:text-white"
            >
              {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
            </button>
          )}
        </div>

        <nav className="flex flex-col gap-1 flex-1">
          <NavItem to="/" end label="Dashboard" icon={LayoutDashboard} collapsed={collapsed} />
          <NavItem to="/projects" end label="Projects" icon={FolderKanban} collapsed={collapsed} />
          <NavItem to="/orgs" end label="Organizations" icon={Building2} collapsed={collapsed} />

          <div className="mt-4">
            {sectionLabel('Admin')}
            <div className="flex flex-col gap-1">
              <NavItem to="/admin/audit" label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
              <NavItem to="/admin/automation" label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
              {me?.role === 'ADMIN' && (
                <NavItem to="/admin/users" label="Users" icon={Users} collapsed={collapsed} variant="sub" />
              )}
            </div>
          </div>

          <div className="mt-4">
            {sectionLabel('Account')}
            <div className="flex flex-col gap-1">
              <NavItem to="/settings/tokens" label="Access Tokens" icon={KeyRound} collapsed={collapsed} variant="sub" />
              <NavItem to="/settings/account" label="Account" icon={User} collapsed={collapsed} variant="sub" />
            </div>
          </div>

          {insideProject && projectKey && (
            <div className="mt-4">
              {sectionLabel('Project')}
              <div className="flex flex-col gap-1">
                <NavItem to={`/p/${projectKey}/dashboard`} label="Dashboard" icon={LayoutDashboard} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/board`} label="Board" icon={Kanban} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/backlog`} label="Backlog" icon={ListChecks} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/sprints`} label="Sprints" icon={CalendarRange} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/issues`} label="Issues" icon={ListTodo} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/reports`} label="Reports" icon={BarChart3} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/automation`} label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
                {serviceDeskConfig?.enabled && (
                  <>
                    <NavItem to={`/p/${projectKey}/service-desk`} label="Service Desk" icon={LifeBuoy} collapsed={collapsed} variant="sub" />
                    <NavItem to={`/p/${projectKey}/incidents`} label="Incidents" icon={AlertTriangle} collapsed={collapsed} variant="sub" />
                  </>
                )}
              </div>

              <div className="mt-4">
                {sectionLabel('Settings')}
                <div className="flex flex-col gap-1">
                  <NavItem to={`/p/${projectKey}/settings/api-keys`} label="API Keys" icon={KeySquare} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/webhooks`} label="Webhooks" icon={Webhook} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/integrations`} label="Integrations" icon={Plug} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/audit`} label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/labels`} label="Labels" icon={Tags} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/versions`} label="Versions" icon={Milestone} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/custom-fields`} label="Custom Fields" icon={SlidersHorizontal} collapsed={collapsed} variant="sub" />
                </div>
              </div>
            </div>
          )}
        </nav>

        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher />{/* collapsed-Prop wird in Task 4 nachgerüstet */}
          <div className={`flex items-center gap-2 ${collapsed ? 'flex-col' : ''}`}>
            <NotificationBell />
            <button
              onClick={logout}
              title={collapsed ? 'Logout' : undefined}
              className={`flex items-center gap-3 px-3 py-2 text-sm text-gray-400 hover:text-white ${collapsed ? 'justify-center' : 'flex-1 text-left'}`}
            >
              <LogOut size={18} className="shrink-0" />
              {!collapsed && 'Logout'}
            </button>
          </div>
          {!collapsed && <VersionTag className="px-3 pt-2" />}
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
    </div>
  )
}
