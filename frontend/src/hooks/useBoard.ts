import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { boardApi } from '@/api/board'

export function useBoard(projectKey: string) {
  return useQuery({
    queryKey: ['board', projectKey],
    queryFn: () => boardApi.getBoard(projectKey).then(r => r.status === 204 ? null : r.data),
  })
}

export function useBacklog(projectKey: string) {
  return useQuery({
    queryKey: ['backlog', projectKey],
    queryFn: () => boardApi.getBacklog(projectKey).then(r => r.data),
  })
}

export function useMoveIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ issueId, newStatusId }: { issueId: string; newStatusId: string }) =>
      boardApi.move(projectKey, issueId, newStatusId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['board', projectKey] }),
  })
}
