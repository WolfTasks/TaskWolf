import { apiClient } from './client'
import type { User, OrgRole } from '@/types'

export interface Organization {
  id: string
  name: string
  slug: string
}

export interface OrganizationMember {
  user: User
  role: OrgRole
}

export interface CreateOrganizationRequest {
  name: string
  slug: string
}

export interface AddMemberRequest {
  userId: string
  role: OrgRole
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
  changeMemberRole: (orgId: string, userId: string, role: OrgRole) =>
    apiClient.patch<OrganizationMember>(`/organizations/${orgId}/members/${userId}`, { role }),
  removeMember: (orgId: string, userId: string) =>
    apiClient.delete(`/organizations/${orgId}/members/${userId}`),

  switchOrg: (orgId: string) =>
    apiClient.post<{ accessToken: string }>(`/auth/switch-org/${orgId}`),
}
