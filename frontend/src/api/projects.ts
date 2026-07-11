import { apiClient } from './client'
import type { Project, ProjectMember, ProjectRole } from '@/types'

export const projectsApi = {
  list: () => apiClient.get<Project[]>('/projects'),
  get: (key: string) => apiClient.get<Project>(`/projects/${key}`),
  create: (data: { key: string; name: string; description?: string }) =>
    apiClient.post<Project>('/projects', data),
  members: (key: string) => apiClient.get<ProjectMember[]>(`/projects/${key}/members`),
  addMember: (key: string, data: { userId: string; role: ProjectRole }) =>
    apiClient.post<ProjectMember>(`/projects/${key}/members`, data),
  updateMemberRole: (key: string, userId: string, data: { role: ProjectRole }) =>
    apiClient.patch<ProjectMember>(`/projects/${key}/members/${userId}`, data),
  removeMember: (key: string, userId: string) =>
    apiClient.delete(`/projects/${key}/members/${userId}`),
}
