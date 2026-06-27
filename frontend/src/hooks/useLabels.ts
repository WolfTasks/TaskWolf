import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { labelsApi } from '@/api/labels'

export function useLabels(projectKey: string) {
  return useQuery({
    queryKey: ['labels', projectKey],
    queryFn: () => labelsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; color: string }) =>
      labelsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}

export function useUpdateLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string; color: string }) =>
      labelsApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}

export function useDeleteLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => labelsApi.delete(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}
