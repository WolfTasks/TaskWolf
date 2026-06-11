import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/comments'

export function useComments(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['comments', projectKey, issueKey],
    queryFn: () => commentsApi.list(projectKey, issueKey).then(r => r.data),
  })
}

export function useActivity(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['activity', projectKey, issueKey],
    queryFn: () => commentsApi.listActivity(projectKey, issueKey).then(r => r.data),
  })
}

export function useAddComment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => commentsApi.create(projectKey, issueKey, body).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comments', projectKey, issueKey] })
      qc.invalidateQueries({ queryKey: ['activity', projectKey, issueKey] })
    },
  })
}

export function useEditComment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ commentId, body }: { commentId: string; body: string }) =>
      commentsApi.edit(projectKey, issueKey, commentId, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comments', projectKey, issueKey] }),
  })
}

export function useDeleteComment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (commentId: string) => commentsApi.delete(projectKey, issueKey, commentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comments', projectKey, issueKey] })
      qc.invalidateQueries({ queryKey: ['activity', projectKey, issueKey] })
    },
  })
}
