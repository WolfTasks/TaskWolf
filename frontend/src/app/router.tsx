import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppLayout } from '@/layouts/AppLayout'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { ProjectListPage } from '@/pages/projects/ProjectListPage'
import { ProjectCreatePage } from '@/pages/projects/ProjectCreatePage'
import { IssueListPage } from '@/pages/issues/IssueListPage'
import { IssueDetailPage } from '@/pages/issues/IssueDetailPage'
import { BoardPage } from '@/pages/board/BoardPage'
import { BacklogPage } from '@/pages/backlog/BacklogPage'
import { ProjectDashboardPage } from '@/pages/project-dashboard/ProjectDashboardPage'
import { ReportsPage } from '@/pages/reports/ReportsPage'
import { NotificationsPage } from '@/pages/notifications/NotificationsPage'
import { WorkflowEditorPage } from '@/pages/settings/WorkflowEditorPage'
import { AutomationPage } from '@/pages/automation/AutomationPage'
import { AutomationRuleEditorPage } from '@/pages/automation/AutomationRuleEditorPage'
import { AdminAutomationPage } from '@/pages/admin/AdminAutomationPage'
import AuditLogPage from '@/pages/admin/AuditLogPage'
import { SsoSettingsPage } from '@/pages/admin/SsoSettingsPage'
import { ApiKeysPage } from '@/pages/settings/ApiKeysPage'
import { WebhooksPage } from '@/pages/settings/WebhooksPage'
import { IntegrationsPage } from '@/pages/settings/IntegrationsPage'
import ProjectAuditPage from '@/pages/projects/settings/ProjectAuditPage'
import { OrgsPage } from '@/pages/orgs/OrgsPage'
import { OrgSettingsPage } from '@/pages/orgs/OrgSettingsPage'
import ServiceDeskPage from '@/pages/projects/servicedesk/ServiceDeskPage'
import IncidentDashboardPage from '@/pages/projects/servicedesk/IncidentDashboardPage'

const isAuthenticated = () => !!localStorage.getItem('accessToken')

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth><AppLayout /></RequireAuth>,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/projects', element: <ProjectListPage /> },
      { path: '/projects/new', element: <ProjectCreatePage /> },
      { path: '/p/:key/issues', element: <IssueListPage /> },
      { path: '/p/:key/issues/:issueKey', element: <IssueDetailPage /> },
      { path: '/p/:key/dashboard', element: <ProjectDashboardPage /> },
      { path: '/p/:key/board', element: <BoardPage /> },
      { path: '/p/:key/backlog', element: <BacklogPage /> },
      { path: '/p/:key/reports', element: <ReportsPage /> },
      { path: '/notifications', element: <NotificationsPage /> },
      { path: '/p/:key/settings/workflow', element: <WorkflowEditorPage /> },
      { path: '/p/:key/automation', element: <AutomationPage /> },
      { path: '/p/:key/automation/new', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/automation/:rid/edit', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/settings/api-keys', element: <ApiKeysPage /> },
      { path: '/p/:key/settings/webhooks', element: <WebhooksPage /> },
      { path: '/p/:key/settings/integrations', element: <IntegrationsPage /> },
      { path: '/p/:key/settings/audit', element: <ProjectAuditPage /> },
      { path: '/admin/automation', element: <AdminAutomationPage /> },
      { path: '/admin/audit', element: <AuditLogPage /> },
      { path: '/admin/settings/sso', element: <SsoSettingsPage /> },
      { path: '/orgs', element: <OrgsPage /> },
      { path: '/orgs/:orgId/settings', element: <OrgSettingsPage /> },
      { path: '/p/:key/service-desk', element: <ServiceDeskPage /> },
      { path: '/p/:key/incidents', element: <IncidentDashboardPage /> },
    ],
  },
])
