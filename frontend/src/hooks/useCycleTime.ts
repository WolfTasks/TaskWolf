import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface SprintCycleTime {
  sprintId: string
  sprintName: string
  averageCycleTimeHours: number | null
}

export interface CycleTimeAggregateData {
  sprints: SprintCycleTime[]
}

export function useCycleTimeAggregate(projectKey: string) {
  return useQuery<CycleTimeAggregateData>({
    queryKey: ['cycleTimeAggregate', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/reports/cycle-time`).then(r => r.data),
  })
}
