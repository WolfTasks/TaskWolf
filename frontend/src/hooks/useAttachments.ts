import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { attachmentsApi } from '@/api/attachments'

export function useAttachments(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['attachments', projectKey, issueKey],
    queryFn: () => attachmentsApi.list(projectKey, issueKey).then(r => r.data),
  })
}

export function useUploadAttachment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => attachmentsApi.upload(projectKey, issueKey, file).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['attachments', projectKey, issueKey] }),
  })
}

export function useDeleteAttachment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (attachmentId: string) => attachmentsApi.delete(projectKey, issueKey, attachmentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['attachments', projectKey, issueKey] }),
  })
}
