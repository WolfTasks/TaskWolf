import { apiClient } from './client'
import type { Attachment } from '@/types'

export const attachmentsApi = {
  list: (projectKey: string, issueKey: string) =>
    apiClient.get<Attachment[]>(`/projects/${projectKey}/issues/${issueKey}/attachments`),

  upload: (projectKey: string, issueKey: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient.post<Attachment>(
      `/projects/${projectKey}/issues/${issueKey}/attachments`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },

  delete: (projectKey: string, issueKey: string, attachmentId: string) =>
    apiClient.delete(`/projects/${projectKey}/issues/${issueKey}/attachments/${attachmentId}`),

  downloadUrl: (projectKey: string, issueKey: string, attachmentId: string) =>
    `/api/v1/projects/${projectKey}/issues/${issueKey}/attachments/${attachmentId}/download`,
}
