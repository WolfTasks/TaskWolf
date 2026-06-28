import qs from 'qs'
import { apiClient } from './client'
import type { Issue, Page } from '@/types'

export const issuesApi = {
  list: (projectKey: string, page = 0, size = 50, labelId?: string, fixVersionId?: string, affectsVersionId?: string, customFieldFilters?: Record<string, string>) => {
    const cf = customFieldFilters
      ? Object.entries(customFieldFilters).map(([k, v]) => `${k}:${v}`)
      : undefined
    return apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, {
      params: {
        page, size,
        ...(labelId ? { labelId } : {}),
        ...(fixVersionId ? { fixVersionId } : {}),
        ...(affectsVersionId ? { affectsVersionId } : {}),
        ...(cf ? { cf } : {}),
      },
      paramsSerializer: (params) => qs.stringify(params, { arrayFormat: 'repeat' }),
    })
  },
  get: (projectKey: string, issueKey: string) =>
    apiClient.get<Issue>(`/projects/${projectKey}/issues/${issueKey}`),
  create: (projectKey: string, data: { title: string; type?: string; priority?: string; description?: string; customFieldValues?: { fieldId: string; value: string | null }[] }) =>
    apiClient.post<Issue>(`/projects/${projectKey}/issues`, data),
  update: (projectKey: string, issueId: string, data: Partial<Issue & { statusId: string }>) =>
    apiClient.patch<Issue>(`/projects/${projectKey}/issues/${issueId}`, data),
}
