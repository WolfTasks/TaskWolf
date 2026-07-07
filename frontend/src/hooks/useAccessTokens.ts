import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export type TokenScope = 'READ_ONLY' | 'READ_WRITE'

export interface AccessTokenItem {
  id: string
  name: string
  tokenPrefix: string
  scope: TokenScope
  lastUsedAt: string | null
  expiresAt: string | null
  createdAt: string | null
}

export interface CreateAccessTokenResponse {
  id: string
  name: string
  tokenPrefix: string
  scope: TokenScope
  plaintext: string
}

export interface CreateAccessTokenBody {
  name: string
  scope: TokenScope
  expiresAt?: string | null
}

export function useAccessTokens() {
  return useQuery<AccessTokenItem[]>({
    queryKey: ['access-tokens'],
    queryFn: () => apiClient.get('/me/tokens').then(r => r.data),
  })
}

export function useCreateAccessToken() {
  const qc = useQueryClient()
  return useMutation<CreateAccessTokenResponse, Error, CreateAccessTokenBody>({
    mutationFn: body => apiClient.post('/me/tokens', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['access-tokens'] }),
  })
}

export function useRevokeAccessToken() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/me/tokens/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['access-tokens'] }),
  })
}
