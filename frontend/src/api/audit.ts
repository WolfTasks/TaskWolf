import { apiClient } from './client'

export interface AuditEvent {
  id: string
  timestamp: string
  userEmail: string
  userId?: string
  projectId?: string
  action: string
  level: string
  resourceType?: string
  resourceId?: string
  ipAddress?: string
}

export const auditApi = {
  listAll: (params: Record<string, string>) =>
    apiClient.get('/admin/audit', { params }).then(r => r.data),
  exportAudit: (format: 'csv' | 'json') =>
    apiClient.get('/admin/audit/export', { params: { format }, responseType: 'blob' }),
  listForProject: (key: string, params: Record<string, string>) =>
    apiClient.get(`/projects/${key}/audit`, { params }).then(r => r.data),
  getConfig: () => apiClient.get('/admin/audit/config').then(r => r.data),
  updateConfig: (level: string, enabled: boolean) =>
    apiClient.put('/admin/audit/config', { level, enabled }),
}
