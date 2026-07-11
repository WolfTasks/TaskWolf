import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'
import type { ProjectRole } from '@/types'

export function useProjectMembers(projectKey: string) {
  return useQuery({
    queryKey: ['members', projectKey],
    queryFn: () => projectsApi.members(projectKey).then(r => r.data),
  })
}

export function useAddMember(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { userId: string; role: ProjectRole }) =>
      projectsApi.addMember(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}

export function useUpdateMemberRole(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: ProjectRole }) =>
      projectsApi.updateMemberRole(projectKey, userId, { role }).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}

export function useRemoveMember(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => projectsApi.removeMember(projectKey, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}
