import { apiClient } from './client'
import type { Sprint } from '@/types'

export const sprintsApi = {
  list: (projectKey: string) =>
    apiClient.get<Sprint[]>(`/projects/${projectKey}/sprints`),
  create: (projectKey: string, data: { name: string; goal?: string; startDate?: string; endDate?: string }) =>
    apiClient.post<Sprint>(`/projects/${projectKey}/sprints`, data),
  update: (projectKey: string, sprintId: string, data: Partial<{ name: string; goal: string; startDate: string; endDate: string }>) =>
    apiClient.patch<Sprint>(`/projects/${projectKey}/sprints/${sprintId}`, data),
  start: (projectKey: string, sprintId: string) =>
    apiClient.post<Sprint>(`/projects/${projectKey}/sprints/${sprintId}/start`),
  complete: (projectKey: string, sprintId: string) =>
    apiClient.post<{ sprint: Sprint; movedToBacklogCount: number }>(`/projects/${projectKey}/sprints/${sprintId}/complete`),
  assignIssue: (projectKey: string, sprintId: string, issueId: string) =>
    apiClient.put(`/projects/${projectKey}/sprints/${sprintId}/issues/${issueId}`),
  unassignIssue: (projectKey: string, sprintId: string, issueId: string) =>
    apiClient.delete(`/projects/${projectKey}/sprints/${sprintId}/issues/${issueId}`),
}
