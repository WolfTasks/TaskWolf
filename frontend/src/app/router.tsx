import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppLayout } from '@/layouts/AppLayout'
import { SettingsLayout } from '@/layouts/SettingsLayout'

// Lazy-Helper: kapselt named- und default-Exports einheitlich als React.lazy.
const lazyPage = (imp: () => Promise<any>, name: string) =>
  lazy(() => imp().then((m) => ({ default: m[name] })))

// Leaf-Pages (lazy). default-Export-Pages nutzen name = 'default'.
const LoginPage = lazyPage(() => import('@/pages/auth/LoginPage'), 'LoginPage')
const RegisterPage = lazyPage(() => import('@/pages/auth/RegisterPage'), 'RegisterPage')
const DashboardPage = lazyPage(() => import('@/pages/dashboard/DashboardPage'), 'DashboardPage')
const ProjectListPage = lazyPage(() => import('@/pages/projects/ProjectListPage'), 'ProjectListPage')
const ProjectCreatePage = lazyPage(() => import('@/pages/projects/ProjectCreatePage'), 'ProjectCreatePage')
const IssueListPage = lazyPage(() => import('@/pages/issues/IssueListPage'), 'IssueListPage')
const IssueDetailPage = lazyPage(() => import('@/pages/issues/IssueDetailPage'), 'IssueDetailPage')
const BoardPage = lazyPage(() => import('@/pages/board/BoardPage'), 'BoardPage')
const BacklogPage = lazyPage(() => import('@/pages/backlog/BacklogPage'), 'BacklogPage')
const SprintsPage = lazyPage(() => import('@/pages/sprints/SprintsPage'), 'SprintsPage')
const ProjectDashboardPage = lazyPage(() => import('@/pages/project-dashboard/ProjectDashboardPage'), 'ProjectDashboardPage')
const ReportsPage = lazyPage(() => import('@/pages/reports/ReportsPage'), 'ReportsPage')
const NotificationsPage = lazyPage(() => import('@/pages/notifications/NotificationsPage'), 'NotificationsPage')
const WorkflowEditorPage = lazyPage(() => import('@/pages/settings/WorkflowEditorPage'), 'WorkflowEditorPage')
const AutomationPage = lazyPage(() => import('@/pages/automation/AutomationPage'), 'AutomationPage')
const AutomationRuleEditorPage = lazyPage(() => import('@/pages/automation/AutomationRuleEditorPage'), 'AutomationRuleEditorPage')
const AdminAutomationPage = lazyPage(() => import('@/pages/admin/AdminAutomationPage'), 'AdminAutomationPage')
const AuditLogPage = lazyPage(() => import('@/pages/admin/AuditLogPage'), 'default')
const SsoSettingsPage = lazyPage(() => import('@/pages/admin/SsoSettingsPage'), 'SsoSettingsPage')
const ApiKeysPage = lazyPage(() => import('@/pages/settings/ApiKeysPage'), 'ApiKeysPage')
const AccessTokensPage = lazyPage(() => import('@/pages/settings/AccessTokensPage'), 'AccessTokensPage')
const AccountSettingsPage = lazyPage(() => import('@/pages/settings/AccountSettingsPage'), 'AccountSettingsPage')
const ProfilePage = lazyPage(() => import('@/pages/settings/ProfilePage'), 'ProfilePage')
const SecurityPage = lazyPage(() => import('@/pages/settings/SecurityPage'), 'SecurityPage')
const NotificationSettingsPage = lazyPage(() => import('@/pages/settings/NotificationSettingsPage'), 'NotificationSettingsPage')
const AdminUsersPage = lazyPage(() => import('@/pages/admin/AdminUsersPage'), 'AdminUsersPage')
const WebhooksPage = lazyPage(() => import('@/pages/settings/WebhooksPage'), 'WebhooksPage')
const IntegrationsPage = lazyPage(() => import('@/pages/settings/IntegrationsPage'), 'IntegrationsPage')
const ProjectAuditPage = lazyPage(() => import('@/pages/projects/settings/ProjectAuditPage'), 'default')
const LabelsPage = lazyPage(() => import('@/pages/projects/settings/LabelsPage'), 'LabelsPage')
const VersionsPage = lazyPage(() => import('@/pages/projects/settings/VersionsPage'), 'VersionsPage')
const CustomFieldsPage = lazyPage(() => import('@/pages/projects/settings/CustomFieldsPage'), 'CustomFieldsPage')
const MembersPage = lazyPage(() => import('@/pages/projects/settings/MembersPage'), 'MembersPage')
const OrganizationSettingsPage = lazyPage(() => import('@/pages/projects/settings/OrganizationSettingsPage'), 'OrganizationSettingsPage')
const OrgsPage = lazyPage(() => import('@/pages/orgs/OrgsPage'), 'OrgsPage')
const OrgSettingsPage = lazyPage(() => import('@/pages/orgs/OrgSettingsPage'), 'OrgSettingsPage')
const ServiceDeskPage = lazyPage(() => import('@/pages/projects/servicedesk/ServiceDeskPage'), 'default')
const IncidentDashboardPage = lazyPage(() => import('@/pages/projects/servicedesk/IncidentDashboardPage'), 'default')

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
      { path: '/p/:key/sprints', element: <SprintsPage /> },
      { path: '/p/:key/reports', element: <ReportsPage /> },
      { path: '/notifications', element: <NotificationsPage /> },
      {
        path: '/settings',
        element: <SettingsLayout />,
        children: [
          { index: true, element: <Navigate to="/settings/profile" replace /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'security', element: <SecurityPage /> },
          { path: 'notifications', element: <NotificationSettingsPage /> },
          { path: 'tokens', element: <AccessTokensPage /> },
          { path: 'account', element: <AccountSettingsPage /> },
        ],
      },
      { path: '/admin/users', element: <AdminUsersPage /> },
      { path: '/p/:key/settings/workflow', element: <WorkflowEditorPage /> },
      { path: '/p/:key/automation', element: <AutomationPage /> },
      { path: '/p/:key/automation/new', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/automation/:rid/edit', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/settings/api-keys', element: <ApiKeysPage /> },
      { path: '/p/:key/settings/webhooks', element: <WebhooksPage /> },
      { path: '/p/:key/settings/integrations', element: <IntegrationsPage /> },
      { path: '/p/:key/settings/audit', element: <ProjectAuditPage /> },
      { path: '/p/:key/settings/labels', element: <LabelsPage /> },
      { path: '/p/:key/settings/versions', element: <VersionsPage /> },
      { path: '/p/:key/settings/custom-fields', element: <CustomFieldsPage /> },
      { path: '/p/:key/settings/members', element: <MembersPage /> },
      { path: '/p/:key/settings/organization', element: <OrganizationSettingsPage /> },
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
