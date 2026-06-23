import { apiClient } from './client'

export interface SsoConfig {
  id: string
  name: string
  issuerUrl: string
  clientId: string
  enabled: boolean
  autoProvision: boolean
}

export interface SsoConfigPublic {
  id: string
  name: string
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
  listPublic: () => apiClient.get<SsoConfigPublic[]>('/admin/sso/public').then(r => r.data),
  list: () => apiClient.get<SsoConfig[]>('/admin/sso').then(r => r.data),
  create: (req: SsoConfigRequest) =>
    apiClient.post<SsoConfig>('/admin/sso', req).then(r => r.data),
  delete: (id: string) => apiClient.delete(`/admin/sso/${id}`),
}
