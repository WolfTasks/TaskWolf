import { useQuery } from '@tanstack/react-query'
import { reportsApi } from '@/api/reports'

export function useBurndown(projectKey: string, sprintId: string | null) {
  return useQuery({
    queryKey: ['burndown', projectKey, sprintId],
    queryFn: () => reportsApi.burndown(projectKey, sprintId!).then(r => r.data),
    enabled: !!sprintId,
  })
}

export function useVelocity(projectKey: string) {
  return useQuery({
    queryKey: ['velocity', projectKey],
    queryFn: () => reportsApi.velocity(projectKey).then(r => r.data),
  })
}
