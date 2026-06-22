import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface ApiKeyItem {
  id: string
  name: string
  keyPrefix: string
  lastUsedAt: string | null
  expiresAt: string | null
  createdAt: string | null
}

export interface CreateApiKeyResponse {
  id: string
  name: string
  keyPrefix: string
  plaintext: string
}

export function useApiKeys(projectKey: string) {
  return useQuery<ApiKeyItem[]>({
    queryKey: ['api-keys', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/api-keys`).then(r => r.data),
  })
}

export function useCreateApiKey(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateApiKeyResponse, Error, { name: string; expiresAt?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/api-keys`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', projectKey] }),
  })
}

export function useRevokeApiKey(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: keyId => apiClient.delete(`/projects/${projectKey}/api-keys/${keyId}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', projectKey] }),
  })
}
