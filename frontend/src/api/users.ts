import { apiClient } from './client'
import type { UserSearchResult } from '@/types'

export const usersApi = {
  search: (q: string) =>
    apiClient.get<UserSearchResult[]>('/users/search', { params: { q } }),
}
