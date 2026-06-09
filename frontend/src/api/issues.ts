import { apiClient } from './client'
import type { Issue, Page } from '@/types'

export const issuesApi = {
  list: (projectKey: string, page = 0, size = 50) =>
    apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, { params: { page, size } }),
  get: (projectKey: string, issueKey: string) =>
    apiClient.get<Issue>(`/projects/${projectKey}/issues/${issueKey}`),
  create: (projectKey: string, data: { title: string; type?: string; priority?: string; description?: string }) =>
    apiClient.post<Issue>(`/projects/${projectKey}/issues`, data),
  update: (projectKey: string, issueId: string, data: Partial<Issue & { statusId: string }>) =>
    apiClient.patch<Issue>(`/projects/${projectKey}/issues/${issueId}`, data),
}
