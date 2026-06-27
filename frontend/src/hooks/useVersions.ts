import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { versionsApi } from '@/api/versions'

export function useVersions(projectKey: string) {
  return useQuery({
    queryKey: ['versions', projectKey],
    queryFn: () => versionsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string }) =>
      versionsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}

export function useUpdateVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string }) =>
      versionsApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}

export function useDeleteVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => versionsApi.delete(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}
