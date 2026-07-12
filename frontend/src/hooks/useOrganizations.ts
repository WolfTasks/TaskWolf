import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, AddMemberRequest } from '@/api/organizations'
import type { OrgRole } from '@/types'

export function useOrgMembers(orgId: string) {
  return useQuery({
    queryKey: ['org-members', orgId],
    queryFn: () => organizationsApi.listMembers(orgId).then(r => r.data),
    enabled: !!orgId,
  })
}

export function useAddOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: AddMemberRequest) => organizationsApi.addMember(orgId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}

export function useChangeOrgMemberRole(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrgRole }) =>
      organizationsApi.changeMemberRole(orgId, userId, role).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}

export function useRemoveOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(orgId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}
