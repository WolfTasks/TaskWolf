import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface ProjectIntegration {
  id: string
  provider: 'GITHUB' | 'GITLAB'
  repoUrl: string | null
  createdAt: string | null
}

export interface CreateIntegrationResponse {
  id: string
  provider: string
  webhookUrl: string
  plaintextSecret: string
  repoUrl: string | null
}

export function useProjectIntegrations(projectKey: string) {
  return useQuery<ProjectIntegration[]>({
    queryKey: ['integrations', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/integrations`).then(r => r.data),
  })
}

export function useCreateIntegration(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateIntegrationResponse, Error, { provider: string; repoUrl?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/integrations`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['integrations', projectKey] }),
  })
}

export function useDeleteIntegration(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/projects/${projectKey}/integrations/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['integrations', projectKey] }),
  })
}
