import { apiClient } from './client'

export interface SsoConfig {
  id: string
  name: string
  issuerUrl: string
  clientId: string
  enabled: boolean
  autoProvision: boolean
}

export interface SsoConfigRequest {
  name: string
  issuerUrl: string
  clientId: string
  clientSecret: string
  enabled?: boolean
  autoProvision?: boolean
}

export const ssoApi = {
  list: () => apiClient.get<SsoConfig[]>('/admin/sso').then(r => r.data),
  create: (req: SsoConfigRequest) =>
    apiClient.post<SsoConfig>('/admin/sso', req).then(r => r.data),
  delete: (id: string) => apiClient.delete(`/admin/sso/${id}`),
}
