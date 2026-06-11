import { apiClient } from './client'
import type { Notification, Page } from '@/types'

export const notificationsApi = {
  list: (page = 0, size = 20) =>
    apiClient.get<Page<Notification>>('/notifications', { params: { page, size } }),

  unreadCount: () =>
    apiClient.get<{ count: number }>('/notifications/unread-count'),

  markRead: (id: string) =>
    apiClient.patch<Notification>(`/notifications/${id}/read`),
}
