import { apiClient } from './client'
import type { Comment, ActivityItem, Page } from '@/types'

export const commentsApi = {
  list: (projectKey: string, issueKey: string) =>
    apiClient.get<Comment[]>(`/projects/${projectKey}/issues/${issueKey}/comments`),

  create: (projectKey: string, issueKey: string, body: string) =>
    apiClient.post<Comment>(`/projects/${projectKey}/issues/${issueKey}/comments`, { body }),

  edit: (projectKey: string, issueKey: string, commentId: string, body: string) =>
    apiClient.put<Comment>(`/projects/${projectKey}/issues/${issueKey}/comments/${commentId}`, { body }),

  delete: (projectKey: string, issueKey: string, commentId: string) =>
    apiClient.delete(`/projects/${projectKey}/issues/${issueKey}/comments/${commentId}`),

  listActivity: (projectKey: string, issueKey: string, page = 0, size = 50) =>
    apiClient.get<Page<ActivityItem>>(`/projects/${projectKey}/issues/${issueKey}/activity`, {
      params: { page, size }
    }),
}
