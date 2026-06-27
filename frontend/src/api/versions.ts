import { apiClient } from './client'
import type { Version } from '@/types'

export const versionsApi = {
  list: (projectKey: string) =>
    apiClient.get<Version[]>(`/projects/${projectKey}/versions`),
  create: (projectKey: string, data: { name: string }) =>
    apiClient.post<Version>(`/projects/${projectKey}/versions`, data),
  update: (projectKey: string, id: string, data: { name: string }) =>
    apiClient.put<Version>(`/projects/${projectKey}/versions/${id}`, data),
  delete: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/versions/${id}`),
}
