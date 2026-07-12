import { Outlet, Link, useNavigate, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  LayoutDashboard, FolderKanban, Building2, ScrollText, Zap, Users,
  Kanban, ListChecks, CalendarRange, ListTodo, BarChart3,
  LifeBuoy, AlertTriangle, KeySquare, Webhook, Plug, Tags, Milestone,
  SlidersHorizontal, ChevronLeft, ChevronRight, LogOut, Settings, UserCog,
} from 'lucide-react'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { OrgSwitcher } from '@/components/OrgSwitcher'
import { authApi } from '@/api/auth'
import { serviceDeskApi } from '@/api/servicedesk'
import { projectsApi } from '@/api/projects'
import { IssueDialogHost } from '@/components/issue/IssueDialogHost'
import { VersionTag } from '@/components/VersionTag'
import { useSidebarCollapsed } from '@/hooks/useSidebarCollapsed'
import { useLanguageSync } from '@/hooks/useLanguageSync'
import { NavItem } from '@/components/nav/NavItem'
import { SidebarSection } from '@/components/nav/SidebarSection'

export function AppLayout() {
  useLanguageSync()
  const { t } = useTranslation('nav')
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

  const { data: currentProject } = useQuery({
    queryKey: ['projects', projectKey],
    queryFn: () => projectsApi.get(projectKey!).then(r => r.data),
    enabled: !!projectKey,
  })

  const logout = async () => {
    try { await authApi.logout() } catch { /* ignore */ }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  return (
    <div id="app-root" className="h-screen overflow-hidden bg-gray-950 text-white flex">
      <aside className={`${collapsed ? 'w-16' : 'w-56'} bg-gray-900 border-r border-gray-800 flex flex-col p-4 transition-[width] duration-200`}>
        <div className={`flex items-center mb-8 ${collapsed ? 'justify-center' : 'justify-between'}`}>
          {!collapsed && <Link to="/" className="text-xl font-bold">🐺 TaskWolf</Link>}
          {!belowBreakpoint && (
            <button
              onClick={toggle}
              title={t(collapsed ? 'sidebar.expand' : 'sidebar.collapse')}
              aria-label={t(collapsed ? 'sidebar.expand' : 'sidebar.collapse')}
              className="p-1 rounded text-gray-400 hover:bg-gray-800 hover:text-white"
            >
              {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
            </button>
          )}
        </div>

        <nav className="flex flex-col gap-1 flex-1 min-h-0 overflow-y-auto">
          <NavItem to="/" end label={t('item.dashboard')} icon={LayoutDashboard} collapsed={collapsed} />
          <NavItem to="/projects" end label={t('item.projects')} icon={FolderKanban} collapsed={collapsed} />
          <NavItem to="/orgs" end label={t('item.organizations')} icon={Building2} collapsed={collapsed} />

          <div className="mt-4">
            <SidebarSection id="admin" label={t('section.admin')} railMode={collapsed}>
              <NavItem to="/admin/audit" label={t('item.audit')} icon={ScrollText} collapsed={collapsed} variant="sub" />
              <NavItem to="/admin/automation" label={t('item.automation')} icon={Zap} collapsed={collapsed} variant="sub" />
              {me?.role === 'ADMIN' && (
                <NavItem to="/admin/users" label={t('item.users')} icon={Users} collapsed={collapsed} variant="sub" />
              )}
            </SidebarSection>
          </div>

          <div className="mt-4">
            <SidebarSection id="account" label={t('section.account')} railMode={collapsed}>
              <NavItem to="/settings" label={t('item.settings')} icon={Settings} collapsed={collapsed} variant="sub" />
            </SidebarSection>
          </div>

          {insideProject && projectKey && (
            <div className="mt-4">
              <SidebarSection id="project" label={t('section.project')} railMode={collapsed}>
                <NavItem to={`/p/${projectKey}/dashboard`} label={t('item.dashboard')} icon={LayoutDashboard} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/board`} label={t('item.board')} icon={Kanban} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/backlog`} label={t('item.backlog')} icon={ListChecks} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/sprints`} label={t('item.sprints')} icon={CalendarRange} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/issues`} label={t('item.issues')} icon={ListTodo} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/reports`} label={t('item.reports')} icon={BarChart3} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/automation`} label={t('item.automation')} icon={Zap} collapsed={collapsed} variant="sub" />
                {serviceDeskConfig?.enabled && (
                  <>
                    <NavItem to={`/p/${projectKey}/service-desk`} label={t('item.serviceDesk')} icon={LifeBuoy} collapsed={collapsed} variant="sub" />
                    <NavItem to={`/p/${projectKey}/incidents`} label={t('item.incidents')} icon={AlertTriangle} collapsed={collapsed} variant="sub" />
                  </>
                )}
              </SidebarSection>

              <div className="mt-4">
                <SidebarSection id="project-settings" label={t('section.settings')} railMode={collapsed}>
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/members`} label={t('item.members')} icon={UserCog} collapsed={collapsed} variant="sub" />
                  )}
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/organization`} label={t('item.organization')} icon={Building2} collapsed={collapsed} variant="sub" />
                  )}
                  <NavItem to={`/p/${projectKey}/settings/api-keys`} label={t('item.apiKeys')} icon={KeySquare} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/webhooks`} label={t('item.webhooks')} icon={Webhook} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/integrations`} label={t('item.integrations')} icon={Plug} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/audit`} label={t('item.audit')} icon={ScrollText} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/labels`} label={t('item.labels')} icon={Tags} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/versions`} label={t('item.versions')} icon={Milestone} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/custom-fields`} label={t('item.customFields')} icon={SlidersHorizontal} collapsed={collapsed} variant="sub" />
                </SidebarSection>
              </div>
            </div>
          )}
        </nav>

        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher collapsed={collapsed} />
          <div className={`flex items-center gap-2 ${collapsed ? 'flex-col' : ''}`}>
            <NotificationBell />
            <button
              onClick={logout}
              title={collapsed ? t('logout') : undefined}
              className={`flex items-center gap-3 px-3 py-2 text-sm text-gray-400 hover:text-white ${collapsed ? 'justify-center' : 'flex-1 text-left'}`}
            >
              <LogOut size={18} className="shrink-0" />
              {!collapsed && t('logout')}
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
