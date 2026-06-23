import { apiClient } from './client'

export interface Organization {
  id: string
  name: string
  slug: string
}

export interface OrganizationMember {
  orgId: string
  userId: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
}

export interface CreateOrganizationRequest {
  name: string
  slug: string
}

export interface AddMemberRequest {
  userId: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
}

export const organizationsApi = {
  listAll: () => apiClient.get<Organization[]>('/organizations'),
  listMine: () => apiClient.get<Organization[]>('/organizations/mine'),
  getById: (id: string) => apiClient.get<Organization>(`/organizations/${id}`),
  create: (data: CreateOrganizationRequest) =>
    apiClient.post<Organization>('/organizations', data),

  listMembers: (orgId: string) =>
    apiClient.get<OrganizationMember[]>(`/organizations/${orgId}/members`),
  addMember: (orgId: string, data: AddMemberRequest) =>
    apiClient.post<OrganizationMember>(`/organizations/${orgId}/members`, data),
  removeMember: (orgId: string, userId: string) =>
    apiClient.delete(`/organizations/${orgId}/members/${userId}`),

  switchOrg: (orgId: string) =>
    apiClient.post<{ accessToken: string }>(`/auth/switch-org/${orgId}`),
}
