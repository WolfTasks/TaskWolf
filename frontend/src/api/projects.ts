import { apiClient } from './client'
import type { Project, User } from '@/types'

export const projectsApi = {
  list: () => apiClient.get<Project[]>('/projects'),
  get: (key: string) => apiClient.get<Project>(`/projects/${key}`),
  create: (data: { key: string; name: string; description?: string }) =>
    apiClient.post<Project>('/projects', data),
  members: (key: string) => apiClient.get<User[]>(`/projects/${key}/members`),
}
