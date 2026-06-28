import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { customFieldsApi } from '@/api/customFields'

export function useCustomFields(projectKey: string) {
  return useQuery({
    queryKey: ['custom-fields', projectKey],
    queryFn: () => customFieldsApi.listDefinitions(projectKey).then(r => r.data),
  })
}

export function useCreateCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; type: string; required: boolean; sortOrder: number }) =>
      customFieldsApi.createDefinition(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useUpdateCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string; type: string; required: boolean; sortOrder: number }) =>
      customFieldsApi.updateDefinition(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useDeleteCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => customFieldsApi.deleteDefinition(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useReorderCustomFields(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (reorders: { id: string; sortOrder: number }[]) =>
      customFieldsApi.reorder(projectKey, reorders),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useCreateOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { label: string; sortOrder: number }) =>
      customFieldsApi.createOption(projectKey, fieldId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useUpdateOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ optId, ...data }: { optId: string; label: string; sortOrder: number }) =>
      customFieldsApi.updateOption(projectKey, fieldId, optId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useDeleteOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (optId: string) => customFieldsApi.deleteOption(projectKey, fieldId, optId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}
