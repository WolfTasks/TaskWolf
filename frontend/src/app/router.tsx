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
    ],
  },
])
