import { apiClient } from './client'
import type { WorkflowEditorData, WorkflowStatus, WorkflowTransition, TransitionGuard, StatusPosition } from '../types'

export const workflowEditorApi = {
  get: (key: string) =>
    apiClient.get<WorkflowEditorData>(`/projects/${key}/workflow`).then(r => r.data),

  createStatus: (key: string, name: string, category: string, color: string) =>
    apiClient.post<WorkflowStatus>(`/projects/${key}/workflow/statuses`, { name, category, color }).then(r => r.data),

  updateStatus: (key: string, sid: string, data: { name?: string; category?: string; color?: string }) =>
    apiClient.put<WorkflowStatus>(`/projects/${key}/workflow/statuses/${sid}`, data).then(r => r.data),

  deleteStatus: (key: string, sid: string) =>
    apiClient.delete(`/projects/${key}/workflow/statuses/${sid}`),

  createTransition: (key: string, fromStatusId: string | null, toStatusId: string) =>
    apiClient.post<WorkflowTransition>(`/projects/${key}/workflow/transitions`, { fromStatusId, toStatusId }).then(r => r.data),

  deleteTransition: (key: string, tid: string) =>
    apiClient.delete(`/projects/${key}/workflow/transitions/${tid}`),

  updateGuards: (key: string, tid: string, guards: TransitionGuard[]) =>
    apiClient.put<WorkflowTransition>(`/projects/${key}/workflow/transitions/${tid}/guards`, { guards }).then(r => r.data),

  saveLayout: (key: string, positions: StatusPosition[]) =>
    apiClient.put(`/projects/${key}/workflow/layout`, { positions }),
}
