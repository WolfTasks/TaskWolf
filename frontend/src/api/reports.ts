import { apiClient } from './client'
import type { BurndownResponse, VelocityResponse } from '@/types'

export const reportsApi = {
  burndown: (projectKey: string, sprintId: string) =>
    apiClient.get<BurndownResponse>(`/projects/${projectKey}/reports/burndown`, { params: { sprintId } }),
  velocity: (projectKey: string) =>
    apiClient.get<VelocityResponse>(`/projects/${projectKey}/reports/velocity`),
}
