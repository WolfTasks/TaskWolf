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
      { path: '/admin/automation', element: <AdminAutomationPage /> },
    ],
  },
])
