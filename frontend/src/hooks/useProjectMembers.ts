import { useQuery } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'

export function useProjectMembers(projectKey: string) {
  return useQuery({
    queryKey: ['members', projectKey],
    queryFn: () => projectsApi.members(projectKey).then(r => r.data),
  })
}
