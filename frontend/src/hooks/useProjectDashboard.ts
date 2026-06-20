import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface WidgetData {
  id: string
  type: string
  config: string | null
  gridX: number
  gridY: number
  gridW: number
  gridH: number
}

export interface DashboardData {
  id: string
  projectId: string
  widgets: WidgetData[]
}

export interface LayoutItem {
  widgetId: string
  gridX: number
  gridY: number
  gridW: number
  gridH: number
}

export interface AddWidgetPayload {
  type: string
  config?: string | null
  gridX?: number
  gridY?: number
  gridW?: number
  gridH?: number
}

export function useProjectDashboard(projectKey: string) {
  return useQuery<DashboardData>({
    queryKey: ['dashboard', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/dashboard`).then(r => r.data),
  })
}

export function useSaveDashboardLayout(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (items: LayoutItem[]) =>
      apiClient.put(`/projects/${projectKey}/dashboard/layout`, items).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}

export function useAddWidget(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: AddWidgetPayload) =>
      apiClient.post(`/projects/${projectKey}/dashboard/widgets`, payload).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}

export function useRemoveWidget(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (widgetId: string) =>
      apiClient.delete(`/projects/${projectKey}/dashboard/widgets/${widgetId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}
