import { apiClient } from './client'
import type { BoardResponse, BacklogResponse } from '@/types'

export const boardApi = {
  getBoard: (projectKey: string) =>
    apiClient.get<BoardResponse>(`/projects/${projectKey}/board`),
  move: (projectKey: string, issueId: string, newStatusId: string) =>
    apiClient.patch(`/projects/${projectKey}/board/move`, { issueId, newStatusId }),
  getBacklog: (projectKey: string) =>
    apiClient.get<BacklogResponse>(`/projects/${projectKey}/backlog`),
}
