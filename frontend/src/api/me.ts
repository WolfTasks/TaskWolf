import { apiClient } from './client'
import type { User } from '@/types'

export interface NotificationPreferenceItem {
  type: string
  inApp: boolean
  email: boolean
}

export const meApi = {
  updateProfile: (displayName: string) =>
    apiClient.patch<User>('/me', { displayName }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.post('/me/password', { currentPassword, newPassword }),
  getNotificationPreferences: () =>
    apiClient.get<{ preferences: NotificationPreferenceItem[] }>('/me/notification-preferences'),
  updateNotificationPreferences: (preferences: NotificationPreferenceItem[]) =>
    apiClient.put<{ preferences: NotificationPreferenceItem[] }>('/me/notification-preferences', { preferences }),
}
