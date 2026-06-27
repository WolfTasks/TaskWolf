import { apiClient } from './client'
import type { Label } from '@/types'

export const labelsApi = {
  list: (projectKey: string) =>
    apiClient.get<Label[]>(`/projects/${projectKey}/labels`),
  create: (projectKey: string, data: { name: string; color: string }) =>
    apiClient.post<Label>(`/projects/${projectKey}/labels`, data),
  update: (projectKey: string, id: string, data: { name: string; color: string }) =>
    apiClient.put<Label>(`/projects/${projectKey}/labels/${id}`, data),
  delete: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/labels/${id}`),
}
