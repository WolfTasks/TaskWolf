import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { sprintsApi } from '@/api/sprints'

export function useSprints(projectKey: string) {
  return useQuery({
    queryKey: ['sprints', projectKey],
    queryFn: () => sprintsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; goal?: string; startDate?: string; endDate?: string }) =>
      sprintsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sprints', projectKey] }),
  })
}

export function useStartSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sprintId: string) => sprintsApi.start(projectKey, sprintId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sprints', projectKey] })
      qc.invalidateQueries({ queryKey: ['board', projectKey] })
      qc.invalidateQueries({ queryKey: ['backlog', projectKey] })
    },
  })
}

export function useCompleteSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sprintId: string) => sprintsApi.complete(projectKey, sprintId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sprints', projectKey] })
      qc.invalidateQueries({ queryKey: ['board', projectKey] })
      qc.invalidateQueries({ queryKey: ['backlog', projectKey] })
    },
  })
}

export function useAssignIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sprintId, issueId }: { sprintId: string; issueId: string }) =>
      sprintsApi.assignIssue(projectKey, sprintId, issueId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['backlog', projectKey] }),
  })
}

export function useUnassignIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sprintId, issueId }: { sprintId: string; issueId: string }) =>
      sprintsApi.unassignIssue(projectKey, sprintId, issueId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['backlog', projectKey] }),
  })
}
