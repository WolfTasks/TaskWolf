import { apiClient } from './client'
import type { CustomFieldDefinition, CustomFieldOption } from '@/types'

export const customFieldsApi = {
  listDefinitions: (projectKey: string) =>
    apiClient.get<CustomFieldDefinition[]>(`/projects/${projectKey}/custom-fields`),

  createDefinition: (projectKey: string, data: { name: string; type: string; required: boolean; sortOrder: number }) =>
    apiClient.post<CustomFieldDefinition>(`/projects/${projectKey}/custom-fields`, data),

  updateDefinition: (projectKey: string, id: string, data: { name: string; type: string; required: boolean; sortOrder: number }) =>
    apiClient.put<CustomFieldDefinition>(`/projects/${projectKey}/custom-fields/${id}`, data),

  reorder: (projectKey: string, reorders: { id: string; sortOrder: number }[]) =>
    apiClient.put(`/projects/${projectKey}/custom-fields/reorder`, reorders),

  deleteDefinition: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/custom-fields/${id}`),

  createOption: (projectKey: string, fieldId: string, data: { label: string; sortOrder: number }) =>
    apiClient.post<CustomFieldOption>(`/projects/${projectKey}/custom-fields/${fieldId}/options`, data),

  updateOption: (projectKey: string, fieldId: string, optId: string, data: { label: string; sortOrder: number }) =>
    apiClient.put<CustomFieldOption>(`/projects/${projectKey}/custom-fields/${fieldId}/options/${optId}`, data),

  deleteOption: (projectKey: string, fieldId: string, optId: string) =>
    apiClient.delete(`/projects/${projectKey}/custom-fields/${fieldId}/options/${optId}`),
}
