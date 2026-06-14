import { apiClient } from './client'
import type { AutomationRule, AutomationRuleDraft, Page } from '../types'

export const automationApi = {
  list: (key: string, page = 0) =>
    apiClient.get<Page<AutomationRule>>(`/projects/${key}/automation/rules`, { params: { page } }).then(r => r.data),

  get: (key: string, rid: string) =>
    apiClient.get<AutomationRule>(`/projects/${key}/automation/rules/${rid}`).then(r => r.data),

  create: (key: string, draft: AutomationRuleDraft) =>
    apiClient.post<AutomationRule>(`/projects/${key}/automation/rules`, draft).then(r => r.data),

  update: (key: string, rid: string, name: string) =>
    apiClient.put<AutomationRule>(`/projects/${key}/automation/rules/${rid}`, { name }).then(r => r.data),

  toggle: (key: string, rid: string) =>
    apiClient.patch<AutomationRule>(`/projects/${key}/automation/rules/${rid}/toggle`).then(r => r.data),

  delete: (key: string, rid: string) =>
    apiClient.delete(`/projects/${key}/automation/rules/${rid}`),

  // Admin
  listSystem: (page = 0) =>
    apiClient.get<Page<AutomationRule>>('/admin/automation/rules', { params: { page } }).then(r => r.data),

  createSystem: (draft: AutomationRuleDraft) =>
    apiClient.post<AutomationRule>('/admin/automation/rules', { ...draft, scope: 'SYSTEM' }).then(r => r.data),

  deleteSystem: (rid: string) =>
    apiClient.delete(`/admin/automation/rules/${rid}`),

  toggleSystem: (rid: string) =>
    apiClient.patch<AutomationRule>(`/admin/automation/rules/${rid}/toggle`).then(r => r.data),
}
